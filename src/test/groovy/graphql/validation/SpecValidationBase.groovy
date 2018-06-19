package graphql.validation

import graphql.parser.Parser
import spock.lang.Specification

/**
 * validation examples used in the spec
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidationBase extends Specification {

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)
    }
}
