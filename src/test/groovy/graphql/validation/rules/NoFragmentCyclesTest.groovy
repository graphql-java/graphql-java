package graphql.validation.rules

import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification

class NoFragmentCyclesTest extends Specification {
    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext, errorCollector)

}
