package graphql.schema;

import graphql.PublicApi;
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

/**
 * Transforms a {@link GraphQLSchema} object.
 */
@PublicApi
public class SchemaTransformer {

    // artificial schema element which serves as root element for the transformation
    private static class DummyRoot implements GraphQLSchemaElement {

        static final String QUERY = "query";
        static final String MUTATION = "mutation";
        static final String SUBSCRIPTION = "subscription";
        static final String ADD_TYPES = "addTypes";
        static final String DIRECTIVES = "directives";
        static final String INTROSPECTION = "introspection";
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
                    .child(QUERY, query);
            if (schema.isSupportingMutations()) {
                builder.child(MUTATION, mutation);
            }
            if (schema.isSupportingSubscriptions()) {
                builder.child(SUBSCRIPTION, subscription);
            }
            builder.children(ADD_TYPES, additionalTypes);
            builder.children(DIRECTIVES, directives);
            builder.child(INTROSPECTION, Introspection.__Schema);
            return builder.build();
        }

        @Override
        public GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
            query = newChildren.getChildOrNull(QUERY);
            mutation = newChildren.getChildOrNull(MUTATION);
            subscription = newChildren.getChildOrNull(SUBSCRIPTION);
            additionalTypes = new LinkedHashSet<>(newChildren.getChildren(ADD_TYPES));
            directives = new LinkedHashSet<>(newChildren.getChildren(DIRECTIVES));
            return this;
        }

        @Override
        public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
            return assertShouldNeverHappen();
        }
    }

    /**
     * Transforms a GrapQLSchema and returns a new GraphQLSchema object.
     *
     * @param schema
     * @param visitor
     *
     * @return a new GraphQLSchema instance.
     */
    public static GraphQLSchema transformSchema(GraphQLSchema schema, GraphQLTypeVisitor visitor) {
        SchemaTransformer schemaTransformer = new SchemaTransformer();
        return schemaTransformer.transform(schema, visitor);
    }

    public GraphQLSchema transform(final GraphQLSchema schema, GraphQLTypeVisitor visitor) {


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
                .buildImpl(true);
        return newSchema;
    }


}
