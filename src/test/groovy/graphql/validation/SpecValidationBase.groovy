package graphql.validation

import graphql.parser.Parser
import spock.lang.Specification

/**
 * validation examples used in the spec
 * https://spec.graphql.org/October2021/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidationBase extends Specification {

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
