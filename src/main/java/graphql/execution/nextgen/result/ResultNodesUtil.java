package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.NonNullableFieldWasNullError;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;
import graphql.util.NodeLocation;
import graphql.util.NodeMultiZipper;
import graphql.util.NodeZipper;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitorStub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.execution.nextgen.result.ResultNodeAdapter.RESULT_NODE_ADAPTER;
import static graphql.util.FpKit.map;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

@Internal
public class ResultNodesUtil {

    public static ExecutionResult toExecutionResult(ExecutionResultNode root) {
        ExecutionResultData executionResultData = toDataImpl(root);
        return ExecutionResultImpl.newExecutionResult()
                .data(executionResultData.data)
                .errors(executionResultData.errors)
                .build();
    }

    private static class ExecutionResultData {
        Object data;
        List<GraphQLError> errors = new ArrayList<>();


        public ExecutionResultData(Object data) {
            this.data = data;
        }

        public ExecutionResultData(Object data, List<GraphQLError> errors) {
            this.data = data;
            this.errors = errors;
        }
    }


    private static ExecutionResultData data(Object data, ExecutionResultNode executionResultNode) {
        return new ExecutionResultData(data, executionResultNode.getFetchedValueAnalysis().getErrors());
    }

    private static ExecutionResultData data(Object data, List<GraphQLError> errors) {
        return new ExecutionResultData(data, errors);
    }

    private static ExecutionResultData data(Object data, NonNullableFieldWasNullException exception) {
        return new ExecutionResultData(data, Arrays.asList(new NonNullableFieldWasNullError(exception)));
    }

    private static ExecutionResultData toDataImpl(ExecutionResultNode root) {
        if (root instanceof LeafExecutionResultNode) {
            return root.getFetchedValueAnalysis().isNullValue() ? data(null, root) : data(((LeafExecutionResultNode) root).getValue(), root);
        }
        if (root instanceof ListExecutionResultNode) {
            Optional<NonNullableFieldWasNullException> childNonNullableException = ((ListExecutionResultNode) root).getChildNonNullableException();
            if (childNonNullableException.isPresent()) {
                return data(null, childNonNullableException.get());
            }
            List<ExecutionResultData> list = map(root.getChildren(), ResultNodesUtil::toDataImpl);
            List<Object> data = map(list, erd -> erd.data);
            List<GraphQLError> errors = new ArrayList<>();
            list.forEach(erd -> errors.addAll(erd.errors));
            return data(data, errors);
        }

        if (root instanceof UnresolvedObjectResultNode) {
            FetchedValueAnalysis fetchedValueAnalysis = root.getFetchedValueAnalysis();
            return data("Not resolved : " + fetchedValueAnalysis.getExecutionStepInfo().getPath() + " with field " + fetchedValueAnalysis.getField(), emptyList());
        }
        if (root instanceof ObjectExecutionResultNode) {
            Optional<NonNullableFieldWasNullException> childrenNonNullableException = ((ObjectExecutionResultNode) root).getChildNonNullableException();
            if (childrenNonNullableException.isPresent()) {
                return data(null, childrenNonNullableException.get());
            }
            Map<String, Object> resultMap = new LinkedHashMap<>();
            List<GraphQLError> errors = new ArrayList<>();
            root.getChildren().forEach(child -> {
                ExecutionResultData executionResultData = toDataImpl(child);
                resultMap.put(child.getMergedField().getResultKey(), executionResultData.data);
                errors.addAll(executionResultData.errors);
            });
            return data(resultMap, errors);
        }
        throw new RuntimeException("Unexpected root " + root);
    }


    public static Optional<NonNullableFieldWasNullException> getFirstNonNullableException(Collection<ExecutionResultNode> collection) {
        return collection.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    public static NonNullableFieldWasNullException newNullableException(FetchedValueAnalysis fetchedValueAnalysis, List<NamedResultNode> children) {
        return newNullableException(fetchedValueAnalysis, children.stream().map(NamedResultNode::getNode).collect(Collectors.toList()));
    }

    public static Map<String, ExecutionResultNode> namedNodesToMap(List<NamedResultNode> namedResultNodes) {
        Map<String, ExecutionResultNode> result = new LinkedHashMap<>();
        for (NamedResultNode namedResultNode : namedResultNodes) {
            result.put(namedResultNode.getName(), namedResultNode.getNode());
        }
        return result;
    }

    public static NonNullableFieldWasNullException newNullableException(FetchedValueAnalysis fetchedValueAnalysis, Collection<ExecutionResultNode> children) {
        // can only happen for the root node
        if (fetchedValueAnalysis == null) {
            return null;
        }
        Assert.assertNotNull(children);
        boolean listIsNonNull = fetchedValueAnalysis.getExecutionStepInfo().isNonNullType();
        if (listIsNonNull) {
            Optional<NonNullableFieldWasNullException> firstNonNullableException = getFirstNonNullableException(children);
            if (firstNonNullableException.isPresent()) {
                return new NonNullableFieldWasNullException(firstNonNullableException.get());
            }
        }
        return null;
    }

    public static List<NodeZipper<ExecutionResultNode>> getUnresolvedNodes(Collection<ExecutionResultNode> roots) {
        List<NodeZipper<ExecutionResultNode>> result = new ArrayList<>();

        ResultNodeTraverser traverser = ResultNodeTraverser.depthFirst();
        traverser.traverse(new TraverserVisitorStub<ExecutionResultNode>() {
            @Override
            public TraversalControl enter(TraverserContext<ExecutionResultNode> context) {
                if (context.thisNode() instanceof UnresolvedObjectResultNode) {
                    result.add(new NodeZipper<>(context.thisNode(), context.getBreadcrumbs(), RESULT_NODE_ADAPTER));
                }
                return TraversalControl.CONTINUE;
            }

        }, roots);
        return result;
    }

    public static NodeMultiZipper<ExecutionResultNode> getUnresolvedNodes(ExecutionResultNode root) {
        List<NodeZipper<ExecutionResultNode>> unresolvedNodes = getUnresolvedNodes(singleton(root));
        return new NodeMultiZipper<>(root, unresolvedNodes, RESULT_NODE_ADAPTER);
    }


    public static NodeLocation key(String name) {
        return new NodeLocation(name, 0);
    }

    public static NodeLocation index(int index) {
        return new NodeLocation(null, index);
    }

}
