package graphql.validation

import graphql.parser.Parser
import spock.lang.Specification

import static graphql.schema.visibility.GraphqlFieldVisibilityEnvironment.newEnvironment

/**
 * validation examples used in the spec
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidationBase extends Specification {

    public static final boolean enableStrictValidation = false

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, newEnvironment().build())
    }
}
