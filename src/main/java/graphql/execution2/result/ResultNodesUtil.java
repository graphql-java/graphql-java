package graphql.execution2.result;

import graphql.Assert;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution2.FetchedValueAnalysis;
import graphql.execution2.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    public static Object toData(ExecutionResultNode root) {
        if (root instanceof LeafExecutionResultNode) {
            return root.getFetchedValueAnalysis().isNullValue() ? null : ((LeafExecutionResultNode) root).getValue();
        }
        if (root instanceof ListExecutionResultNode) {
            if (((ListExecutionResultNode) root).getChildNonNullableException().isPresent()) {
                return null;
            }
            return root.getChildren().stream().map(ResultNodesUtil::toData).collect(Collectors.toList());
        }

        if (root instanceof UnresolvedObjectResultNode) {
            FetchedValueAnalysis fetchedValueAnalysis = root.getFetchedValueAnalysis();
            return "Not resolved : " + fetchedValueAnalysis.getExecutionStepInfo().getPath() + " with subSelection " + fetchedValueAnalysis.getFieldSubSelection().toShortString();
        }
        if (root instanceof ObjectExecutionResultNode) {
            if (((ObjectExecutionResultNode) root).getChildrenNonNullableException().isPresent()) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            ((ObjectExecutionResultNode) root).getChildrenMap().forEach((key, value) -> result.put(key, toData(value)));
            return result;
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

    public static List<ExecutionResultNodeZipper> getUnresolvedNodes(Collection<ExecutionResultNode> roots) {
        List<ExecutionResultNodeZipper> result = new ArrayList<>();

        ResultNodeTraverser resultNodeTraverser = new ResultNodeTraverser(new ResultNodeVisitor() {
            @Override
            public void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs) {
                if (node instanceof UnresolvedObjectResultNode) {
                    result.add(new ExecutionResultNodeZipper(node, breadcrumbs));
                }
            }
        });
        roots.forEach(resultNodeTraverser::traverse);
        return result;
    }

    public static MultiZipper getUnresolvedNodes(ExecutionResultNode root) {
        List<ExecutionResultNodeZipper> zippers = new ArrayList<>();

        ResultNodeTraverser resultNodeTraverser = new ResultNodeTraverser(new ResultNodeVisitor() {
            @Override
            public void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs) {
                if (node instanceof UnresolvedObjectResultNode) {
                    zippers.add(new ExecutionResultNodeZipper(node, breadcrumbs));
                }
            }
        });
        resultNodeTraverser.traverse(root);
        return new MultiZipper(root, zippers);
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
