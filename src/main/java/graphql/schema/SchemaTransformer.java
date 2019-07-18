package graphql.schema;

import graphql.introspection.Introspection;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TreeTransformer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.schema.GraphQLSchemaElementAdapter.SCHEMA_ELEMENT_ADAPTER;
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;

public class SchemaTransformer {

    private static class DummyRoot implements GraphQLSchemaElement {

        GraphQLSchema schema;

        GraphQLObjectType query;
        GraphQLObjectType mutation;
        GraphQLObjectType subscription;
        Set<GraphQLType> additionalTypes;
        Set<GraphQLDirective> directives;

        DummyRoot(GraphQLSchema schema) {
            this.schema = schema;
            query = schema.getQueryType();
            mutation = schema.isSupportingMutations() ? schema.getMutationType() : null;
            subscription = schema.isSupportingSubscriptions() ? schema.getSubscriptionType() : null;
            additionalTypes = schema.getAdditionalTypes();
            directives = new LinkedHashSet<>(schema.getDirectives());
        }


        @Override
        public List<GraphQLSchemaElement> getChildren() {
            return assertShouldNeverHappen();
        }

        @Override
        public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
            SchemaElementChildrenContainer.Builder builder = newSchemaElementChildrenContainer()
                    .child("query", query);
            if (schema.isSupportingMutations()) {
                builder.child("mutation", mutation);
            }
            if (schema.isSupportingSubscriptions()) {
                builder.child("subscription", subscription);
            }
            builder.children("addTypes", additionalTypes);
            builder.children("directives", directives);
            builder.child("introspection", Introspection.__Schema);
            return builder.build();
        }

        @Override
        public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
            query = newChildren.getChildOrNull("query");
            mutation = newChildren.getChildOrNull("mutation");
            subscription = newChildren.getChildOrNull("subscription");
            additionalTypes = new LinkedHashSet<>(newChildren.getChildren("addTypes"));
            directives = new LinkedHashSet<>(newChildren.getChildren("directives"));
            return this;
        }

        @Override
        public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
            return assertShouldNeverHappen();
        }
    }

    ;

    public GraphQLSchema transformWholeSchema(final GraphQLSchema schema, GraphQLTypeVisitor visitor) {


        DummyRoot dummyRoot = new DummyRoot(schema);
        TraverserVisitor<GraphQLSchemaElement> traverserVisitor = new TraverserVisitor<GraphQLSchemaElement>() {
            @Override
            public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
                if (context.thisNode() == dummyRoot) {
                    return TraversalControl.CONTINUE;
                }
                return context.thisNode().accept(context, visitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
                return TraversalControl.CONTINUE;
            }
        };

        TreeTransformer<GraphQLSchemaElement> treeTransformer = new TreeTransformer<>(SCHEMA_ELEMENT_ADAPTER);
        treeTransformer.transform(dummyRoot, traverserVisitor);
        GraphQLSchema newSchema = GraphQLSchema.newSchema()
                .query(dummyRoot.query)
                .mutation(dummyRoot.mutation)
                .subscription(dummyRoot.subscription)
                .additionalTypes(dummyRoot.additionalTypes)
                .additionalDirectives(dummyRoot.directives)
                .build();
        return newSchema;
    }


}
