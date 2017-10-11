package graphql.validation

import graphql.introspection.Introspection.DirectiveLocation
import graphql.parser.Parser
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLSchema
import spock.lang.Specification

/**
 * Test that custom directives are validated properly.
 */
class ValidateCustomDirectives extends Specification {

    GraphQLDirective customDirective = GraphQLDirective.newDirective()
            .name("customDirective")
            .description("Dummy Custom Directive")
            .validLocations(DirectiveLocation.FIELD)
            .build()

    GraphQLSchema customDirectiveSchema = GraphQLSchema.newSchema()
            .query(SpecValidationSchema.queryRoot)
            .build(SpecValidationSchema.specValidationDictionary, [customDirective].toSet())

    def 'Schema with custom directive validates query with same directive'() {
        def query = """
query {
  dog {
    name @customDirective
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def 'Schema with custom directive validates query parameters'() {
        def query = """
query {
  dog {
    name @customDirective(dummy: true)
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownDirective
        validationErrors.get(0).getDescription() == 'Unknown directive argument dummy'
    }

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(customDirectiveSchema, document)
    }
}
