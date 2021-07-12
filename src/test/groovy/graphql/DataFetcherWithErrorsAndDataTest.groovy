package graphql

import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.language.SourceLocation
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.ExecutionInput.newExecutionInput
import static graphql.GraphqlErrorBuilder.newError
import static graphql.Scalars.GraphQLString
import static graphql.execution.DataFetcherResult.newResult
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

/**
 * A set of tests to show how a data fetcher can return errors and data and field context
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class DataFetcherWithErrorsAndDataTest extends Specification {

    class ChildObject {
        String goodField = "good"
        String badField = null
    }

    class ParentObject {
        ChildObject child = new ChildObject()
    }

    def executionInput(String query) {
        newExecutionInput().query(query).build()
    }

    @Unroll
    def "#820 - data fetcher can return data and errors (strategy: #strategyName)"() {

        // see https://github.com/graphql-java/graphql-java/issues/820

        given:


        GraphQLOutputType childType = newObject()
                .name("childType")
                .field(newFieldDefinition().name("goodField")
                        .type(GraphQLString))
                .field(newFieldDefinition().name("badField")
                        .type(GraphQLString))
                .build()
        GraphQLOutputType parentType = newObject()
                .name("parentType")
                .field(newFieldDefinition().name("child")
                        .type(childType))
                .build()
        GraphQLOutputType rootType = newObject()
                .name("rootType")
                .field(newFieldDefinition().name("parent")
                        .type(parentType)
                        .dataFetcher({ env ->
                            newResult()
                                    .data(new ParentObject())
                                    .errors([newError()
                                                     .message("badField is bad")
                                                     .path(["root", "parent", "child", "badField"])
                                                     .location(env.getField().getSourceLocation())
                                                     .build()])
                                    .build()

                        }))
                .build()

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                                .name("root")
                                .type(rootType)
                                .dataFetcher({ env -> [:] })

                        ))
                .build()

        def query = """
        query { 
            root {
                parent {
                    child {
                        goodField
                        badField
                    }
                }
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result.errors.size() == 1
        result.errors[0].path == ["root", "parent", "child", "badField"]
        result.errors[0].message == "badField is bad"
        result.errors[0].locations == [new SourceLocation(4, 17)]

        result.data["root"]["parent"]["child"]["goodField"] == "good"
        result.data["root"]["parent"]["child"]["badField"] == null

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }

    @Unroll
    def "#820 - data fetcher can return multiple errors (strategy: #strategyName)"() {

        // see https://github.com/graphql-java/graphql-java/issues/820

        given:


        GraphQLOutputType childType = newObject()
                .name("childType")
                .field(newFieldDefinition().name("goodField")
                        .type(GraphQLString))
                .field(newFieldDefinition().name("badField")
                        .type(GraphQLString))
                .build()
        GraphQLOutputType parentType = newObject()
                .name("parentType")
                .field(newFieldDefinition().name("child")
                        .type(childType)
                        .dataFetcher({ env ->
                            newResult()
                                    .data(["goodField": null, "badField": null])
                                    .errors([
                                            newError().message("goodField is bad")
                                                    .path(["root", "parent", "child", "goodField"])
                                                    .location(env.getField().getSourceLocation())
                                                    .build(),
                                            newError().message("badField is bad")
                                                    .path(["root", "parent", "child", "badField"])
                                                    .location(env.getField().getSourceLocation())
                                                    .build()
                                    ]).build()
                        }))
                .build()
        GraphQLOutputType rootType = newObject()
                .name("rootType")
                .field(newFieldDefinition().name("parent")
                        .type(parentType))
                .build()

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                                .name("root")
                                .type(rootType)
                                .dataFetcher({ env -> ["parent": [:]] })

                        ))
                .build()

        def query = """
        query { 
            root {
                parent {
                    child {
                        goodField
                        badField
                    }
                }
            }
        }
        """

        def result = GraphQL
                .newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(executionInput(query))

        expect:

        result.errors.size() == 2
        result.errors[0].path == ["root", "parent", "child", "goodField"]
        result.errors[0].message == "goodField is bad"
        result.errors[0].locations == [new SourceLocation(5, 21)]
        result.errors[1].path == ["root", "parent", "child", "badField"]
        result.errors[1].message == "badField is bad"
        result.errors[1].locations == [new SourceLocation(5, 21)]

        result.data["root"]["parent"]["child"]["goodField"] == null
        result.data["root"]["parent"]["child"]["badField"] == null

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }


    @Unroll
    def "data fetcher can return context down each level (strategy: #strategyName)"() {
        given:

        def spec = '''
            type Query {
                first : Level1
            }
            
            type Level1 {
                second : Level2 
            }
            
            type Level2 {
                third : Level3
            }
            
            type Level3 {
                skip : Level4
            }    

            type Level4 {
                fourth : String
            }    
        '''


        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query",
                        { type ->
                            type.dataFetcher("first", new ContextPassingDataFetcher())
                        })
                .type("Level1",
                        { type ->
                            type.dataFetcher("second", new ContextPassingDataFetcher())
                        })
                .type("Level2",
                        { type ->
                            type.dataFetcher("third", new ContextPassingDataFetcher())
                        })
                .type("Level3",
                        { type ->
                            type.dataFetcher("skip", new ContextPassingDataFetcher(true))
                        })
                .type("Level4",
                        { type ->
                            type.dataFetcher("fourth", new ContextPassingDataFetcher())
                        })
                .build()

        def query = '''
            {
                first {
                    second {
                        third {
                            skip {
                                fourth
                            }
                        }
                    }
                }
            }
        '''

        def result = TestUtil.graphQL(spec, runtimeWiring)
                .queryExecutionStrategy(executionStrategy)
                .build()
                .execute(newExecutionInput().query(query).root("").context(1))

        expect:

        result.errors.isEmpty()
        result.data == [first: [second: [third: [skip: [fourth: "1,2,3,4,4,"]]]]]

        where:

        strategyName  | executionStrategy
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()

    }
}