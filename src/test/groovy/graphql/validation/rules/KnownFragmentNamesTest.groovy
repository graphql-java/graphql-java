package graphql.validation.rules

import graphql.language.FragmentSpread
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class KnownFragmentNamesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    KnownFragmentNames knownFragmentNames = new KnownFragmentNames(validationContext, errorCollector)

    def "unknown fragment reference in fragment spread"() {
        given:
        FragmentSpread fragmentSpread = FragmentSpread.newFragmentSpread("fragment").build()
        knownFragmentNames.validationContext.getFragment("fragment") >> null
        when:
        knownFragmentNames.checkFragmentSpread(fragmentSpread)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UndefinedFragment)

    }


}
