package graphql.schema;

public class SchemaTransformer {


//    public GraphQLSchema transformWholeSchema(GraphQLSchema graphQLSchema, GraphQLTypeVisitor nodeVisitor) {
//    }

    public GraphQLSchemaElement transform(GraphQLSchemaElement root, GraphQLTypeVisitor graphQLTypeVisitor) {
//        assertNotNull(root);
//        assertNotNull(graphQLTypeVisitor);
//
//        TraverserVisitor<Node> traverserVisitor = new TraverserVisitor<Node>() {
//            @Override
//            public TraversalControl enter(TraverserContext<Node> context) {
//                return context.thisNode().accept(context, nodeVisitor);
//            }
//
//            @Override
//            public TraversalControl leave(TraverserContext<Node> context) {
//                return TraversalControl.CONTINUE;
//            }
//        };
//
//        TreeTransformer<Node> treeTransformer = new TreeTransformer<>(AST_NODE_ADAPTER);
//        return treeTransformer.transform(root, traverserVisitor);
        return null;

    }
}
