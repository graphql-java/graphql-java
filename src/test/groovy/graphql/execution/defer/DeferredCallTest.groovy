package graphql.execution.defer


import graphql.ExecutionResultImpl
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
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

    def "test error capture happens via CF"() {
        given:
        def errorSupport = new DeferredCallContext()
        errorSupport.onError(new ValidationError(ValidationErrorType.MissingFieldArgument))
        errorSupport.onError(new ValidationError(ValidationErrorType.FieldsConflict))

        DeferredCall call = new DeferredCall(parse("/path"), {
            completedFuture(new ExecutionResultImpl("some data", [new ValidationError(ValidationErrorType.FieldUndefined)]))
        }, errorSupport)

        when:
        def future = call.invoke()
        def er = future.join()

        then:
        er.errors.size() == 3
        er.errors[0].message.contains("Validation error of type FieldUndefined")
        er.errors[1].message.contains("Validation error of type MissingFieldArgument")
        er.errors[2].message.contains("Validation error of type FieldsConflict")
        er.path == ["path"]
    }

    private static Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>> createResolvedFieldCall(
            String fieldName,
            Object data
    ) {
        return new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                return completedFuture(
                        new DeferredCall.FieldWithExecutionResult(fieldName,
                                new ExecutionResultImpl(data, Collections.emptyList())
                        )
                )
            }
        }
    }
}
