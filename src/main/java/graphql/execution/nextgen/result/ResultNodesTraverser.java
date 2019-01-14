//package graphql.execution.nextgen.result;
//
//import graphql.execution.nextgen.result.ExecutionResultNode;
//import graphql.util.Traverser;
//import graphql.util.TraverserVisitor;
//
//public class ResultNodesTraverser {
//
//    private final Traverser<ExecutionResultNode> traverser;
//
//    private ResultNodesTraverser(Traverser<ExecutionResultNode> traverser) {
//        this.traverser = traverser;
//    }
//
//    public static ResultNodesTraverser depthFirst() {
//        return new ResultNodesTraverser(Traverser.depthFirstWithNamedChildren(ExecutionResultNode::getNamedChildren, null, null);
//    }
//
//    public void traverse(TraverserVisitor<ExecutionResultNode> visitor, ExecutionResultNode root) {
//        traverser.traverse(root, visitor);
//    }
//
//}
