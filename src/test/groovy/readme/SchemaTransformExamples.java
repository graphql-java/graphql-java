package readme;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.util.TraversalControl.CONTINUE;

@SuppressWarnings("ALL")
public class SchemaTransformExamples {

    GraphQLSchema schema;

    void trasnformSchema() {
        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                // we need to change __XXX introspection types to have directive extensions
                if (someConditionalLogic(objectType)) {
                    GraphQLObjectType newObjectType = buildChangedObjectType(objectType, codeRegistry);
                    return changeNode(context, newObjectType);
                }
                return CONTINUE;
            }

            private boolean someConditionalLogic(GraphQLObjectType objectType) {
                // up to you to decide what causes a change, perhaps a directive is on the element
                return objectType.hasDirective("specialDirective");
            }

            private GraphQLObjectType buildChangedObjectType(GraphQLObjectType objectType, GraphQLCodeRegistry.Builder codeRegistry) {
                GraphQLFieldDefinition newField = GraphQLFieldDefinition.newFieldDefinition()
                        .name("newField").type(Scalars.GraphQLString).build();
                GraphQLObjectType newObjectType = objectType.transform(builder -> builder.field(newField));

                DataFetcher newDataFetcher = dataFetchingEnvironment -> {
                    return "someValueForTheNewField";
                };
                FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType.getName(), newField.getName());
                codeRegistry.dataFetcher(coordinates, newDataFetcher);
                return newObjectType;
            }
        };
        GraphQLSchema newSchema = SchemaTransformer.transformSchema(schema, visitor);
    }

    void example_commands() {

        GraphQLSchemaElement updatedElement = null;
        GraphQLSchemaElement newElement = null;

        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {

                // changes the current element in the schema
                changeNode(context, updatedElement);

                // inserts a new element after the current one in the schema
                insertAfter(context, newElement);

                // inserts a new element before the current one in teh schema
                insertBefore(context, newElement);

                // deletes the current element from the schema
                deleteNode(context);


                return CONTINUE;
            }
        };
    }
}
