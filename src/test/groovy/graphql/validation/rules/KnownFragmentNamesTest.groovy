package graphql.validation.rules

import graphql.language.FragmentSpread
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification


class KnownFragmentNamesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    KnownFragmentNames knownFragmentNames = new KnownFragmentNames(validationContext, errorCollector)

    def "unknown fragment reference in fragment spread"() {
        given:
        FragmentSpread fragmentSpread = new FragmentSpread("fragment")
        knownFragmentNames.validationContext.getFragment("fragment") >> null
        when:
        knownFragmentNames.checkFragmentSpread(fragmentSpread);

        then:
        errorCollector.containsError(ValidationErrorType.UndefinedFragment)

    }


}
