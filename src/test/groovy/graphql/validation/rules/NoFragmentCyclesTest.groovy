package graphql.validation.rules

import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import spock.lang.Specification

class NoFragmentCyclesTest extends Specification {
    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    NoFragmentCycles noFragmentCycles = new NoFragmentCycles(validationContext, errorCollector)

}
