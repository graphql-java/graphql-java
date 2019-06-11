package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.TypeName
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class KnownTypeNamesTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, errorCollector)

    def "unknown types is an error"() {
        given:
        knownTypeNames.validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        knownTypeNames.checkTypeName(TypeName.newTypeName("Simpson").build())

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownType)

    }
}
