package graphql.execution

import graphql.ErrorType
import graphql.ExceptionWhileDataFetching
import graphql.GraphQLError
import graphql.language.SourceLocation
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.util.concurrent.CompletionException

import static graphql.Scalars.GraphQLString
import static graphql.execution.DataFetcherExceptionHandlerParameters.newExceptionParameters
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.execution.MergedField.newMergedField
import static graphql.language.Field.newField
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment

class SimpleDataFetcherExceptionHandlerTest extends Specification {
    def handler = new SimpleDataFetcherExceptionHandler()

    class CustomException extends RuntimeException implements GraphQLError {
        String msg

        CustomException(String msg) {
            this.msg = msg
        }

        @Override
        String getMessage() {
            return msg
        }

        @Override
        List<SourceLocation> getLocations() {
            return []
        }

        @Override
        ErrorType getErrorType() {
            return ErrorType.DataFetchingException
        }
    }

    def "will wrap general exceptions"() {
        when:
        def handlerParameters = mkParams(new RuntimeException("RTE"))
        def result = handler.onException(handlerParameters)

        then:
        result.errors[0] instanceof ExceptionWhileDataFetching
        result.errors[0].getMessage().contains("RTE")
    }

    def "can use GraphQLError implementations "() {
        def graphqlErrEx = new CustomException("BANG")
        when:
        def result = handler.onException(mkParams(graphqlErrEx))

        then:
        result.errors[0] == graphqlErrEx
    }

    def "can unwrap certain exceptions"() {
        def graphqlErrEx = new CustomException("BANG")
        when:
        def result = handler.onException(mkParams(new CompletionException(graphqlErrEx)))

        then:
        result.errors[0] == graphqlErrEx
    }

    def "wont unwrap other exceptions"() {
        def graphqlErrEx = new CustomException("BANG")
        when:
        def result = handler.onException(mkParams(new RuntimeException(graphqlErrEx)))

        then:
        result.errors[0] instanceof ExceptionWhileDataFetching
        result.errors[0].getMessage().contains("BANG")
    }

    private static DataFetcherExceptionHandlerParameters mkParams(Exception exception) {
        def mergedField = newMergedField(newField("f").build()).build()
        def esi = newExecutionStepInfo()
                .field(mergedField)
                .type(GraphQLString).path(ResultPath.fromList(["hi"])).build()
        DataFetchingEnvironment env = newDataFetchingEnvironment().
                mergedField(mergedField).executionStepInfo(esi).build()
        newExceptionParameters().exception(exception).dataFetchingEnvironment(env).build()
    }
}
