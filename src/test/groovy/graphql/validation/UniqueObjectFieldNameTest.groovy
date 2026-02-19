package graphql.validation

import graphql.parser.Parser
import graphql.validation.Validator
import spock.lang.Specification

class UniqueObjectFieldNameTest extends Specification {

    def 'Object Field Name Uniqueness Not Valid'() {
        def query = """
           query {
             dogWithInput(leash: {
                id: "foo"
                id: "bar"
             }) {
                name
             }
           }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(Harness.Schema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].message == "Validation Error (UniqueObjectFieldName@[dogWithInput]) : There can be only one field named 'id'"
    }

}
