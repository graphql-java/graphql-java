package graphql

import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

/**
 * A set of tests to show how non null field handling correctly bubble up or not
 */
class NonNullHandlingTest extends Specification {

    @SuppressWarnings("GroovyUnusedDeclaration")
    class ParentTypeImplementation {
        String nullChild = null
        String nonNullChild = "not null"
    }

    def executionInput(String query) {
        ExecutionInput.newExecutionInput().query(query).build()
    }

    def "#268 - null child field values are allowed in nullable parent type"() {

        // see https://github.com/graphql-java/graphql-java/issues/268

        given:


        GraphQLOutputType parentType = newObject()
                .name("currentType")
                .field(newFieldDefinition().name("nullChild")
                .type(nonNull(GraphQLString)))
                .field(newFieldDefinition().name("nonNullChild")
                .type(nonNull(GraphQLString)))
                .build()

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                        .name("parent")
                        .type(parentType) // nullable parent
                        .dataFetcher({ env -> new ParentTypeImplementation() })

                ))
                .build()

        def query = """
        query { 
            parent {
                nonNullChild
                nullChild
            }
        }
        """

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(executionInput(query))

        then:

        result.errors.size() == 1
        result.data["parent"] == null
    }

    def "#268 - null child field values are NOT allowed in non nullable parent types"() {

        // see https://github.com/graphql-java/graphql-java/issues/268

        given:


        GraphQLOutputType parentType = newObject()
                .name("currentType")
                .field(newFieldDefinition().name("nullChild")
                .type(nonNull(GraphQLString)))
                .field(newFieldDefinition().name("nonNullChild")
                .type(nonNull(GraphQLString)))
                .build()

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(
                        newFieldDefinition()
                                .name("parent")
                                .type(nonNull(parentType)) // non nullable parent
                                .dataFetcher({ env -> new ParentTypeImplementation() })

                ))
                .build()

        def query = """
        query { 
            parent {
                nonNullChild
                nullChild
            }
        }
        """

        when:
        def result = GraphQL.newGraphQL(schema).build().execute(executionInput(query))

        then:

        result.errors.size() == 1
        result.data == null
    }
}