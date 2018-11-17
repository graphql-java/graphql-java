package graphql.execution2.result;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.execution.NonNullableFieldWasNullError;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution2.FetchedValueAnalysis;
import graphql.execution2.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.execution2.result.ExecutionResultNodePosition.index;
import static graphql.execution2.result.ExecutionResultNodePosition.key;

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

    private static ExecutionResultData data(Object data) {
        return new ExecutionResultData(data);
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
            return root.getFetchedValueAnalysis().isNullValue() ? data(null) : data(((LeafExecutionResultNode) root).getValue(), root);
        }
        if (root instanceof ListExecutionResultNode) {
            Optional<NonNullableFieldWasNullException> childNonNullableException = ((ListExecutionResultNode) root).getChildNonNullableException();
            if (childNonNullableException.isPresent()) {
                return data(null, childNonNullableException.get());
            }
            List<ExecutionResultData> list = root.getChildren().stream().map(ResultNodesUtil::toDataImpl).collect(Collectors.toList());
            List<Object> data = list
                    .stream()
                    .map(erd -> erd.data)
                    .collect(Collectors.toList());
            List<GraphQLError> errors = new ArrayList<>();
            list.forEach(erd -> errors.addAll(erd.errors));
            return data(data, errors);
        }

        if (root instanceof UnresolvedObjectResultNode) {
            FetchedValueAnalysis fetchedValueAnalysis = root.getFetchedValueAnalysis();
            return data("Not resolved : " + fetchedValueAnalysis.getExecutionStepInfo().getPath() + " with subSelection " + fetchedValueAnalysis.getFieldSubSelection().toShortString());
        }
        if (root instanceof ObjectExecutionResultNode) {
            Optional<NonNullableFieldWasNullException> childrenNonNullableException = ((ObjectExecutionResultNode) root).getChildrenNonNullableException();
            if (childrenNonNullableException.isPresent()) {
                return data(null, childrenNonNullableException.get());
            }
            Map<String, Object> resultMap = new LinkedHashMap<>();
            List<GraphQLError> errors = new ArrayList<>();
            ((ObjectExecutionResultNode) root).getChildrenMap().forEach((key, value) -> {
                ExecutionResultData executionResultData = toDataImpl(value);
                resultMap.put(key, executionResultData.data);
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

    public static List<ExecutionResultZipper> getUnresolvedNodes(Collection<ExecutionResultNode> roots) {
        List<ExecutionResultZipper> result = new ArrayList<>();

        ResultNodeTraverser resultNodeTraverser = new ResultNodeTraverser(new ResultNodeVisitor() {
            @Override
            public void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs) {
                if (node instanceof UnresolvedObjectResultNode) {
                    result.add(new ExecutionResultZipper(node, breadcrumbs));
                }
            }
        });
        roots.forEach(resultNodeTraverser::traverse);
        return result;
    }

    public static ExecutionResultMultiZipper getUnresolvedNodes(ExecutionResultNode root) {
        List<ExecutionResultZipper> zippers = new ArrayList<>();

        ResultNodeTraverser resultNodeTraverser = new ResultNodeTraverser(new ResultNodeVisitor() {
            @Override
            public void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs) {
                if (node instanceof UnresolvedObjectResultNode) {
                    zippers.add(new ExecutionResultZipper(node, breadcrumbs));
                }
            }
        });
        resultNodeTraverser.traverse(root);
        return new ExecutionResultMultiZipper(root, zippers);
    }


    public interface ResultNodeVisitor {

        void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs);

    }

    private static class ResultNodeTraverser {

        ResultNodeVisitor visitor;
        Deque<Breadcrumb> breadCrumbsStack = new ArrayDeque<>();

        public ResultNodeTraverser(ResultNodeVisitor visitor) {
            this.visitor = visitor;
        }

        public void traverse(ExecutionResultNode node) {
            if (node instanceof ObjectExecutionResultNode) {
                ((ObjectExecutionResultNode) node).getChildrenMap().forEach((name, child) -> {
                    breadCrumbsStack.push(new Breadcrumb(node, key(name)));
                    traverse(child);
                    breadCrumbsStack.pop();
                });
            }
            if (node instanceof ListExecutionResultNode) {
                List<ExecutionResultNode> children = node.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    breadCrumbsStack.push(new Breadcrumb(node, index(i)));
                    traverse(children.get(i));
                    breadCrumbsStack.pop();
                }
            }
            List<Breadcrumb> breadcrumbs = new ArrayList<>(breadCrumbsStack);
            visitor.visit(node, breadcrumbs);
        }

    }

}
