package graphql.execution.defer

import graphql.ExecutionResultImpl
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.validation.ValidationErrorType.FieldUndefined
import static graphql.validation.ValidationErrorType.FieldsConflict
import static graphql.validation.ValidationErrorType.MissingFieldArgument
import static java.util.concurrent.CompletableFuture.completedFuture

class DeferCallTest extends Specification {

    def "test call capture gives a CF"() {
        given:
        DeferredCall call = new DeferredCall({
            completedFuture(new ExecutionResultImpl("some data", Collections.emptyList()))
        }, new DeferredErrorSupport())

        when:
        def future = call.invoke()
        then:
        future.join().data == "some data"
    }

    def "test error capture happens via CF"() {
        given:
        def errorSupport = new DeferredErrorSupport()
        errorSupport.onError(new ValidationError(ValidationErrorType.MissingFieldArgument))
        errorSupport.onError(new ValidationError(ValidationErrorType.FieldsConflict))

        DeferredCall call = new DeferredCall({
            completedFuture(new ExecutionResultImpl("some data", [new ValidationError(FieldUndefined)]))
        }, errorSupport)

        when:
        def future = call.invoke()
        def er = future.join()

        then:
        er.errors.size() == 3
        (er.errors[0] as ValidationError).validationErrorType == FieldUndefined
        (er.errors[1] as ValidationError).validationErrorType == MissingFieldArgument
        (er.errors[2] as ValidationError).validationErrorType == FieldsConflict
    }
}
