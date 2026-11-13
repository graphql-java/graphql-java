package graphql.validation

import graphql.language.SourceLocation
import spock.lang.Specification

class ValidationErrorToString extends Specification {

    def 'toString prints correctly ValidationError object when all fields are initialized'() {
        given:
        def sourceLocations = [new SourceLocation(5, 0), new SourceLocation(10, 1)]
        def description = "Validation Error (UnknownType)"
        def validationErrorClassification = ValidationErrorType.UnknownType
        def queryPath = ["home", "address"]
        def extensions = ["extension1": "first", "extension2": true, "extension3": 2]

        when:
        def validationError = ValidationError
                .newValidationError()
                .sourceLocations(sourceLocations)
                .description(description)
                .validationErrorType(validationErrorClassification)
                .queryPath(queryPath)
                .extensions(extensions)
                .build()

        then:
        validationError.toString() == "ValidationError{validationErrorType=UnknownType, queryPath=[home, address], message=Validation Error (UnknownType), locations=[SourceLocation{line=5, column=0}, SourceLocation{line=10, column=1}], description='Validation Error (UnknownType)', extensions=[extension1=first, extension2=true, extension3=2]}"
    }

    def 'toString prints correctly ValidationError object when optional fields are empty'() {
       when:
        def validationError = ValidationError
                .newValidationError()
                .description("Test error")
                .build()

        then:
        validationError.toString() == "ValidationError{validationErrorType=null, queryPath=[], message=Test error, locations=[], description='Test error', extensions=[]}"
    }
}
