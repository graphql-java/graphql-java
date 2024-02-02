package graphql.execution.incremental

import graphql.ExecutionResultImpl
import graphql.GraphQLError
import graphql.execution.NonNullableFieldWasNullException
import graphql.execution.ResultPath
import graphql.execution.incremental.DeferredCall
import graphql.execution.incremental.DeferredCallContext
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

import static graphql.execution.ResultPath.parse
import static java.util.concurrent.CompletableFuture.completedFuture

class DeferredCallTest extends Specification {

    def "test call capture gives a CF"() {
        given:
        DeferredCall call = new DeferredCall("my-label", parse("/path"),
                [createResolvedFieldCall("field", "some data")], new DeferredCallContext())

        when:
        def future = call.invoke()
        then:
        future.join().toSpecification() == [
                label: "my-label",
                path : ["path"],
                data : [field: "some data"]
        ]
    }

    def "multiple field calls are resolved together"() {
        given:
        DeferredCall call = new DeferredCall("my-label", parse("/path"),
                [
                        createResolvedFieldCall("field1", "some data 1"),
                        createResolvedFieldCall("field2", "some data 2"),
                        createResolvedFieldCall("field3", "some data 3")
                ],
                new DeferredCallContext()
        )

        when:
        def future = call.invoke()
        then:
        future.join().toSpecification() == [
                label: "my-label",
                path : ["path"],
                data : [field1: "some data 1", field2: "some data 2", field3: "some data 3"]
        ]
    }

    def "can handle non-nullable field error"() {
        given:
        def deferredCallContext = new DeferredCallContext()
        def mockedException = Mock(NonNullableFieldWasNullException) {
            getMessage() >> "Field value can't be null"
            getPath() >> ResultPath.parse("/path")
        }

        DeferredCall call = new DeferredCall("my-label", parse("/path"), [
                createFieldCallThatThrowsException(mockedException),
                createResolvedFieldCall("field1", "some data")
        ], deferredCallContext)

        when:
        def future = call.invoke()
        def deferPayload = future.join()

        then:
        deferPayload.toSpecification() == [
                path  : ["path"],
                label : "my-label",
                errors: [
                        [
                                message   : "Field value can't be null",
                                path      : ["path"],
                                extensions: [classification: "NullValueInNonNullableField"]
                        ]
                ],
        ]
    }

    private static Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>> createResolvedFieldCall(
            String fieldName,
            Object data
    ) {
        return createResolvedFieldCall(fieldName, data, Collections.emptyList())
    }

    private static Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>> createResolvedFieldCall(
            String fieldName,
            Object data,
            List<GraphQLError> errors
    ) {
        return new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                return completedFuture(
                        new DeferredCall.FieldWithExecutionResult(fieldName,
                                new ExecutionResultImpl(data, errors)
                        )
                )
            }
        }
    }

    private static Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>> createFieldCallThatThrowsException(
            Throwable exception
    ) {
        return new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                return CompletableFuture.failedFuture(exception)
            }
        }
    }
}
