package graphql.validation.rules

import graphql.validation.TraversalContext
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import spock.lang.Specification


class NoUnusedVariablesTest extends Specification {


    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    NoUnusedVariables noUnusedVariables = new NoUnusedVariables(validationContext, errorCollector)

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getTraversalContext() >> traversalContext
    }
}
