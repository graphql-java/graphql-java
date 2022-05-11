package graphql.execution

import graphql.ErrorType
import graphql.GraphQL
import graphql.GraphQLError
import graphql.TestUtil
import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput

class DataFetcherExceptionHandlerTest extends Specification {

    class CustomError implements GraphQLError {
        String msg
        SourceLocation sourceLocation

        CustomError(String msg, SourceLocation sourceLocation) {
            this.msg = msg
            this.sourceLocation = sourceLocation
        }

        @Override
        String getMessage() {
            return msg
        }

        @Override
        List<SourceLocation> getLocations() {
            return [sourceLocation]
        }

        @Override
        ErrorType getErrorType() {
            return ErrorType.DataFetchingException
        }
    }

    private static GraphQL mkTestingGraphQL(DataFetcherExceptionHandler handler) {
        def dataFetchers = [
                Query: [field: { env -> throw new RuntimeException("BANG") } as DataFetcher]
        ]

        def graphQL = TestUtil.graphQL('''
            type Query {
                field : String
            }
        ''', dataFetchers)
                .queryExecutionStrategy(new AsyncExecutionStrategy(handler))
                .build()
        graphQL
    }

    def "integration test to prove custom error handle can be made"() {
        DataFetcherExceptionHandler handler = new DataFetcherExceptionHandler() {
            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
                def msg = "The thing went " + handlerParameters.getException().getMessage()
                return CompletableFuture.completedFuture(
                        DataFetcherExceptionHandlerResult.newResult().error(new CustomError(msg, handlerParameters.getSourceLocation())).build()
                )
            }
        }

        GraphQL graphQL = mkTestingGraphQL(handler)
        when:
        def result = graphQL.execute(newExecutionInput().query(' { field }'))
        then:
        !result.errors.isEmpty()
        result.errors[0].message == "The thing went BANG"
    }


    def "integration test to prove an async custom error handle can be made"() {
        DataFetcherExceptionHandler handler = new DataFetcherExceptionHandler() {
            @Override
            DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
                return null
            }

            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters params) {
                def msg = "The thing went " + params.getException().getMessage()
                def result = DataFetcherExceptionHandlerResult.newResult().error(new CustomError(msg, params.getSourceLocation())).build()
                return CompletableFuture.supplyAsync({ -> result })
            }
        }

        GraphQL graphQL = mkTestingGraphQL(handler)

        when:
        def result = graphQL.execute(newExecutionInput().query(' { field }'))
        then:
        !result.errors.isEmpty()
        result.errors[0].message == "The thing went BANG"
    }

    def "if an exception handler itself throws an exception than that is handled"() {
        DataFetcherExceptionHandler handler = new DataFetcherExceptionHandler() {
            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
                throw new RuntimeException("The handler itself went BANG!")
            }
        }

        GraphQL graphQL = mkTestingGraphQL(handler)

        when:
        def result = graphQL.execute(newExecutionInput().query(' { field }'))
        then:
        !result.errors.isEmpty()
        result.errors[0].message.contains("The handler itself went BANG!")
    }

    def "if an async exception handler itself throws an exception than that is handled"() {
        DataFetcherExceptionHandler handler = new DataFetcherExceptionHandler() {
            @Override
            DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
                return null
            }

            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
                throw new RuntimeException("The handler itself went BANG!")
            }
        }

        GraphQL graphQL = mkTestingGraphQL(handler)

        when:
        def result = graphQL.execute(newExecutionInput().query(' { field }'))
        then:
        !result.errors.isEmpty()
        result.errors[0].message.contains("The handler itself went BANG!")
    }


    def "multiple errors can be returned in a handler"() {
        DataFetcherExceptionHandler handler = new DataFetcherExceptionHandler() {
            @Override
            DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
                return null
            }

            @Override
            CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters params) {
                def result = DataFetcherExceptionHandlerResult.newResult()
                for (int i = 0; i < 5; i++) {
                    def msg = "$i The thing went " + params.getException().getMessage()
                    result.error(new CustomError(msg, params.getSourceLocation()))
                }
                return CompletableFuture.supplyAsync({ -> result.build() })
            }
        }

        GraphQL graphQL = mkTestingGraphQL(handler)

        when:
        def result = graphQL.execute(newExecutionInput().query(' { field }'))

        then:
        result.errors.size() == 5
    }
}
