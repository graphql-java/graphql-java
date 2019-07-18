package graphql.schema;

import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TreeTransformer;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLSchemaElementAdapter.SCHEMA_ELEMENT_ADAPTER;

public class SchemaTransformer {


    public GraphQLSchema transformWholeSchema(GraphQLSchema graphQLSchema, GraphQLTypeVisitor nodeVisitor) {
        return null;
    }

    public GraphQLSchemaElement transform(GraphQLSchemaElement root, GraphQLTypeVisitor visitor) {
        assertNotNull(root);
        assertNotNull(visitor);

        TraverserVisitor<GraphQLSchemaElement> traverserVisitor = new TraverserVisitor<GraphQLSchemaElement>() {
            @Override
            public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
                return context.thisNode().accept(context, visitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
                return TraversalControl.CONTINUE;
            }
        };

        TreeTransformer<GraphQLSchemaElement> treeTransformer = new TreeTransformer<>(SCHEMA_ELEMENT_ADAPTER);
        return treeTransformer.transform(root, traverserVisitor);
    }
}
