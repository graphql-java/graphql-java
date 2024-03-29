package graphql.execution


import graphql.ExceptionWhileDataFetching
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


    def "will wrap general exceptions"() {
        when:
        def handlerParameters = mkParams(new RuntimeException("RTE"))
        def result = handler.handleException(handlerParameters)

        then:
        result.join().errors[0] instanceof ExceptionWhileDataFetching
        result.join().errors[0].getMessage().contains("RTE")
    }

    def "can unwrap certain exceptions"() {
        when:
        def result = handler.handleException(mkParams(new CompletionException(new RuntimeException("RTE"))))

        then:
        result.join().errors[0] instanceof ExceptionWhileDataFetching
        result.join().errors[0].getMessage().contains("RTE")
    }

    def "wont unwrap other exceptions"() {
        when:
        def result = handler.handleException(mkParams(new RuntimeException("RTE",new RuntimeException("BANG"))))

        then:
        result.join().errors[0] instanceof ExceptionWhileDataFetching
        ! result.join().errors[0].getMessage().contains("BANG")
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
