package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.TypeName
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import spock.lang.Specification


class KnownTypeNamesTest extends Specification {

    ErrorCollector errorCollector = new ErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    KnownTypeNames knownTypeNames = new KnownTypeNames(validationContext, errorCollector)

    def "unknown types is an error"() {
        given:
        knownTypeNames.validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        knownTypeNames.checkTypeName(new TypeName("Simpson"))

        then:
        errorCollector.getErrors().size() > 0

    }
}
