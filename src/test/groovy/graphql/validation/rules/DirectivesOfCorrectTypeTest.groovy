package graphql.validation.rules

import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import spock.lang.Specification


class DirectivesOfCorrectTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    DirectivesOfCorrectType directivesOfCorrectType = new DirectivesOfCorrectType(validationContext, errorCollector)

    def ""() {

    }
}
