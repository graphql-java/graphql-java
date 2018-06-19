package graphql

import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.DataFetcherResult
import graphql.execution.ExecutorServiceExecutionStrategy
import graphql.language.SourceLocation
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static java.util.concurrent.ForkJoinPool.commonPool

/**
 * A set of tests to show how a data fetcher can return errors and data
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
        ExecutionInput.newExecutionInput().query(query).build()
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
            new DataFetcherResult(new ParentObject(),
                    [new DataFetchingErrorGraphQLError("badField is bad", ["child", "badField"])])
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
        result.errors[0].locations == [new SourceLocation(6, 27)]

        result.data["root"]["parent"]["child"]["goodField"] == "good"
        result.data["root"]["parent"]["child"]["badField"] == null

        where:

        strategyName  | executionStrategy
        'executor'    | new ExecutorServiceExecutionStrategy(commonPool())
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
            new DataFetcherResult(
                    ["goodField": null, "badField": null], [
                    new DataFetchingErrorGraphQLError("goodField is bad", ["goodField"]),
                    new DataFetchingErrorGraphQLError("badField is bad", ["badField"])
            ])
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
        result.errors[0].locations == [new SourceLocation(7, 31)]
        result.errors[1].path == ["root", "parent", "child", "badField"]
        result.errors[1].message == "badField is bad"
        result.errors[1].locations == [new SourceLocation(7, 31)]

        result.data["root"]["parent"]["child"]["goodField"] == null
        result.data["root"]["parent"]["child"]["badField"] == null

        where:

        strategyName  | executionStrategy
        'executor'    | new ExecutorServiceExecutionStrategy(commonPool())
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }

    @Unroll
    def "#820 - data fetcher can return multiple errors on one field, but collapsed (strategy: #strategyName)"() {

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
            new DataFetcherResult(
                    null, [
                    new DataFetchingErrorGraphQLError("error 1", []),
                    new DataFetchingErrorGraphQLError("error 2", [])
            ])
        }))
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                        .name("parent")
                        .type(parentType)
                        .dataFetcher({ env -> [:] })

                ))
                .build()

        def query = """
        query { 
            parent {
                child {
                    goodField
                    badField
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
        result.errors[0].path == ["parent", "child"]
        result.errors[0].message == "error 1"
        result.errors[0].locations == [new SourceLocation(6, 27)]

        result.errors[1].path == ["parent", "child"]
        result.errors[1].message == "error 2"
        result.errors[1].locations == [new SourceLocation(6, 27)]

        result.data["parent"]["child"] == null

        where:

        strategyName  | executionStrategy
        'executor'    | new ExecutorServiceExecutionStrategy(commonPool())
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }

    @Unroll
    def "#820 - data fetcher can return data and errors as completable future (strategy: #strategyName)"() {

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
            CompletableFuture.completedFuture(new DataFetcherResult(
                    new ChildObject(), [
                    new DataFetchingErrorGraphQLError("badField is bad", ["badField"])
            ]))
        }))
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                        .name("parent")
                        .type(parentType)
                        .dataFetcher({ env -> [:] })

                ))
                .build()

        def query = """
        query { 
            parent {
                child {
                    goodField
                    badField
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
        result.errors[0].path == ["parent", "child", "badField"]
        result.errors[0].message == "badField is bad"
        result.errors[0].locations == [new SourceLocation(6, 27)]

        result.data["parent"]["child"]["goodField"] == "good"
        result.data["parent"]["child"]["badField"] == null
        where:

        strategyName  | executionStrategy
        'executor'    | new ExecutorServiceExecutionStrategy(commonPool())
        'async'       | new AsyncExecutionStrategy()
        'asyncSerial' | new AsyncSerialExecutionStrategy()
    }
}