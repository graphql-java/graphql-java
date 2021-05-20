package graphql.execution

import graphql.language.SourceLocation
import graphql.schema.DataFetchingEnvironmentImpl
import spock.lang.Specification

import java.util.function.Predicate

import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.execution.MergedField.newMergedField
import static graphql.language.Field.newField

class DelegatingDataFetcherExceptionHandlerTest extends Specification {

    def "delegates only to matching delegate"() {
        given: 'a delegate that matches and a delegate that does not match'
        def matchingDelegate = Mock(DataFetcherExceptionHandler)
        def notMatchingDelegate = Mock(DataFetcherExceptionHandler)
        def expectedResult = Mock(DataFetcherExceptionHandlerResult)
        def delegates = new LinkedHashMap<Predicate<Throwable>, DataFetcherExceptionHandler>()
        delegates.put({ t -> true } as Predicate, matchingDelegate)
        delegates.put({ t -> false } as Predicate, notMatchingDelegate)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .build()
        def handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(new RuntimeException())
                .build();
        def handler = new DelegatingDataFetcherExceptionHandler(delegates)

        when: 'onException invoked'
        def actualResult = handler.onException(handlerParameters)

        then: 'only the matching delegate is invoked'
        1 * matchingDelegate.onException(handlerParameters) >> expectedResult
        0 * _

        and: 'the actual result is equal to the expected result'
        actualResult == expectedResult
    }

    def "default used"() {
        given: 'a delegates that do not match'
        def location = new SourceLocation(6, 9)
        def field = newMergedField(newField("f").sourceLocation(location).build()).build()
        def stepInfo = newExecutionStepInfo().path(ResultPath.fromList(["a", "b"])).type(GraphQLString).build()
        def notMatchingDelegate = Mock(DataFetcherExceptionHandler)
        def delegates = new LinkedHashMap<Predicate<Throwable>, DataFetcherExceptionHandler>()
        delegates.put({ t -> false } as Predicate, notMatchingDelegate)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .mergedField(field)
                .executionStepInfo(stepInfo)
                .build()
        def handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(new RuntimeException())
                .build();
        def handler = new DelegatingDataFetcherExceptionHandler(delegates)

        when: 'onException invoked'
        def actualResult = handler.onException(handlerParameters)

        then: 'none of the delegates are invoked'
        0 * _

        and: 'the actual result is not null'
        actualResult
    }

    def "default when overridden is used"() {
        given: 'a delegates that do not match'
        def notMatchingDelegate = Mock(DataFetcherExceptionHandler)
        def defaultDelegate = Mock(DataFetcherExceptionHandler)
        def expectedResult = Mock(DataFetcherExceptionHandlerResult)
        def delegates = new LinkedHashMap<Predicate<Throwable>, DataFetcherExceptionHandler>()
        delegates.put({ t -> false } as Predicate, notMatchingDelegate)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .build()
        def handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(new RuntimeException())
                .build();
        def handler = new DelegatingDataFetcherExceptionHandler(delegates)
        handler.defaultHandler = defaultDelegate

        when: 'onException invoked'
        def actualResult = handler.onException(handlerParameters)

        then: 'only the matching delegate is invoked'
        1 * defaultDelegate.onException(_) >> expectedResult
        0 * _

        and: 'the actual result is equal to the expected result'
        actualResult == expectedResult
    }

    def "fromThrowableTypeMapping only delegates to matching delegate"() {
        given: 'a delegate that matches and a delegate that does not match'
        def matchingDelegate = Mock(DataFetcherExceptionHandler)
        def notMatchingDelegate = Mock(DataFetcherExceptionHandler)
        def expectedResult = Mock(DataFetcherExceptionHandlerResult)
        def delegates = new LinkedHashMap<Throwable, DataFetcherExceptionHandler>()
        delegates.put(IllegalStateException, matchingDelegate)
        delegates.put(IllegalArgumentException, notMatchingDelegate)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .build()
        def handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(new IllegalStateException())
                .build();
        def handler = DelegatingDataFetcherExceptionHandler.fromThrowableTypeMapping(delegates)

        when: 'onException invoked'
        def actualResult = handler.onException(handlerParameters)

        then: 'only the matching delegate is invoked'
        1 * matchingDelegate.onException(handlerParameters) >> expectedResult
        0 * _

        and: 'the actual result is equal to the expected result'
        actualResult == expectedResult
    }

    def "fromThrowableTypeMapping only delegates to matching delegate when subclass exception"() {
        given: 'a delegate that matches and a delegate that does not match'
        def matchingDelegate = Mock(DataFetcherExceptionHandler)
        def notMatchingDelegate = Mock(DataFetcherExceptionHandler)
        def expectedResult = Mock(DataFetcherExceptionHandlerResult)
        def delegates = new LinkedHashMap<Throwable, DataFetcherExceptionHandler>()
        delegates.put(IllegalStateException, notMatchingDelegate)
        delegates.put(IllegalArgumentException, matchingDelegate)
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .build()
        def handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(new IllegalFormatException())
                .build();
        def handler = DelegatingDataFetcherExceptionHandler.fromThrowableTypeMapping(delegates)

        when: 'onException invoked'
        def actualResult = handler.onException(handlerParameters)

        then: 'only the matching delegate is invoked'
        1 * matchingDelegate.onException(handlerParameters) >> expectedResult
        0 * _

        and: 'the actual result is equal to the expected result'
        actualResult == expectedResult
    }
}
