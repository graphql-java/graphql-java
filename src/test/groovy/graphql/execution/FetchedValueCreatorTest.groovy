package graphql.execution

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.Scalars
import graphql.language.Field
import graphql.schema.DataFetchingEnvironmentImpl
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class FetchedValueCreatorTest extends Specification {
    private static def EXCEPTION_HANDLER = new SimpleDataFetcherExceptionHandler();

    // We don't care about the data fetching environment, it only needs to survive calls by SimpleDataFetcherExceptionHandler
    private static def DATA_FETCHING_ENVIRONMENT = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
            .mergedField(MergedField.newMergedField(Field.newField().build()).build())
            .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo().type(Scalars.GraphQLString).path(ResultPath.parse("/path")).build())
            .build();

    private static def createFetchedValue(ValueUnboxer unboxer, Object object) {
        return FetchedValueCreator.unbox(unboxer, { exception, unboxingContext ->
            def exceptionHandlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                    .exception(exception)
                    .dataFetchingEnvironment(unboxingContext.getDataFetchingEnvironment())
                    .build()
            def exceptionHandlerResult = EXCEPTION_HANDLER.onException(exceptionHandlerParameters);
            exceptionHandlerResult.errors.each { unboxingContext.addError(it) }
        }, DATA_FETCHING_ENVIRONMENT, object)
    }

    def "unboxes value for Optional"() {
        given:
        def unboxer = ValueUnboxers.DEFAULT

        when:
        def fetchedValue = createFetchedValue(unboxer, result)

        then:
        fetchedValue.fetchedValue == expected

        where:
        result                    || expected

        Optional.of("hello")      || "hello"
        Optional.ofNullable(null) || null

        OptionalInt.of(10)        || 10
        OptionalInt.empty()       || null

        OptionalDouble.of(10)     || 10D
        OptionalDouble.empty()    || null

        OptionalLong.of(10)       || 10L
        OptionalLong.empty()      || null
    }

    def "unboxes value for Future"() {
        given:
        def unboxer = ValueUnboxers.DEFAULT

        when:
        def fetchedValue = createFetchedValue(unboxer, result)

        then:
        fetchedValue.fetchedValue == expected
        fetchedValue.errors.size() == errors.size()
        errorsEndWith(fetchedValue.errors, errors)

        where:
        result                                                                                  || expected || errors

        CompletableFuture.completedFuture(null)                                                 || null     || []
        CompletableFuture.completedFuture("hello")                                              || "hello"  || []
        new CompletableFuture().tap { it.completeExceptionally(new RuntimeException("Hello")) } || null     || ["Hello"]
    }

    static def errorsEndWith(List<GraphQLError> errors, List<String> ends) {
        for (int i = 0; i < errors.size(); i++) {
            if (!errors[i].message.endsWith(ends[i])) {
                return false
            }
        }
        return true
    }

    def "unboxes value for DataFetcherResult"() {
        given:
        def unboxer = ValueUnboxers.DEFAULT

        when:
        def fetchedValue = createFetchedValue(unboxer, result)

        then:
        fetchedValue.fetchedValue == expected
        fetchedValue.errors.size() == errors.size()
        errorsEndWith(fetchedValue.errors, errors)
        fetchedValue.localContext == localContext

        where:
        result                                                                              || expected || errors               || localContext

        DataFetcherResult.newResult().data("hello").localContext("abc").build()             || "hello"  || []                   || "abc"
        DataFetcherResult.newResult().error(error("Hello")).build()                         || null     || ["Hello"]            || null
        DataFetcherResult.newResult().error(error("Hello1")).error(error("Hello2")).build() || null     || ["Hello1", "Hello2"] || null
    }


    static def error(String message) {
        return GraphqlErrorBuilder.newError().message(message).build()
    }

    def "unboxes deeply boxed"() {
        given:
        def unboxer = ValueUnboxers.DEFAULT

        when:
        def fetchedValue = createFetchedValue(unboxer, result)

        then:
        fetchedValue.fetchedValue == expected

        where:
        result                                                                                                                                              || expected

        Optional.of(CompletableFuture.completedFuture(DataFetcherResult.newResult().data(CompletableFuture.completedFuture(Optional.of("hello"))).build())) || "hello"
    }

    private static class MyBox {
        final Object value;
        final String error;

        MyBox(Object value, String error) {
            this.value = value
            this.error = error
        }

        static MyBox value(Object value) {
            return new MyBox(value, null)
        }

        static MyBox error(String error) {
            return new MyBox(null, error)
        }
    }


    private class MyBoxUnboxer implements ValueUnboxer {
        @Override
        Object unbox(Object object, ValueUnboxingContext valueUnboxingContext) {
            if (object instanceof MyBox) {
                if (object.error == null) {
                    return valueUnboxingContext.unbox(object.value)
                } else {
                    valueUnboxingContext.addError(error(object.error))
                    return null
                }
            }
            return object;
        }
    }

    def "custom unboxer"() {
        given:
        def unboxer = new MyBoxUnboxer()

        when:
        def fetchedValue = createFetchedValue(unboxer, result)

        then:
        fetchedValue.fetchedValue == expected
        fetchedValue.errors.size() == errors.size()
        errorsEndWith(fetchedValue.errors, errors)

        where:
        result                            || expected || errors

        "hello"                           || "hello"  || []
        MyBox.value("hello")              || "hello"  || []
        MyBox.error("Hello")              || null     || ["Hello"]
        MyBox.value(MyBox.value("hello")) || "hello"  || []
    }
}
