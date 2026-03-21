package graphql.validation

import graphql.parser.Parser
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.FIELD
import static graphql.schema.GraphQLNonNull.nonNull

class ProvidedNonNullArgumentsTest extends Specification {

    def "not provided non null field argument results in error"() {
        def query = """
            query getDogName {
              dog {
                doesKnowCommand
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.MissingFieldArgument }
    }

    def "not provided and not defaulted non null field argument with error message"() {
        def query = """
            query getDogName {
              dog {
                  doesKnowCommand
              }
            }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MissingFieldArgument
        validationErrors[0].message == "Validation error (MissingFieldArgument@[dog/doesKnowCommand]) : Missing field argument 'dogCommand'"
    }

    def "all field arguments are provided results in no error"() {
        def query = """
            query getDog {
              dog {
                doesKnowCommand(dogCommand: SIT)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "not provided not defaulted directive argument results in error"() {
        def query = """
            query getDogName {
              dog @nonNullDirective {
                name
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.MissingDirectiveArgument }
    }

    def "not provided and not defaulted non null directive argument with error message"() {
        def query = """
            query getDogName {
              dog @nonNullDirective {
                  name
              }
            }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MissingDirectiveArgument
        validationErrors[0].message == "Validation error (MissingDirectiveArgument@[dog]) : Missing directive argument 'arg1'"
    }

    def "all directive arguments are provided results in no error"() {
        def query = """
            query getDogName {
              dog @nonNullDirective(arg1: "value") {
                name
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "provide the explicit value null is not valid for non null argument"() {
        def query = """
            query getDogName {
              dog {
                doesKnowCommand(dogCommand: null)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.any { it.validationErrorType == ValidationErrorType.NullValueForNonNullArgument }
    }

    def "provide the explicit value null is not valid for non null argument with error message"() {
        def query = """
            query getDogName {
              dog {
                  doesKnowCommand(dogCommand: null)
              }
            }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors[0].validationErrorType == ValidationErrorType.NullValueForNonNullArgument
        validationErrors[0].message == "Validation error (NullValueForNonNullArgument@[dog/doesKnowCommand]) : Null value for non-null field argument 'dogCommand'"
    }

    def "not provided but defaulted non null field argument is not an error"() {
        def schema = GraphQLSchema.newSchema()
            .query(GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                    .name("field")
                    .type(GraphQLString)
                    .argument(GraphQLArgument.newArgument()
                        .name("arg")
                        .type(nonNull(GraphQLString))
                        .defaultValueProgrammatic("defaultVal")))
                .build())
            .build()
        def document = new Parser().parseDocument('{ field }')
        when:
        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)
        then:
        !errors.any { it.validationErrorType == ValidationErrorType.MissingFieldArgument }
    }

    def "not provided but defaulted directive argument is not an error"() {
        def directive = GraphQLDirective.newDirective()
            .name("myDirective")
            .validLocation(FIELD)
            .argument(GraphQLArgument.newArgument()
                .name("arg")
                .type(nonNull(GraphQLString))
                .defaultValueProgrammatic("defaultVal"))
            .build()
        def schema = GraphQLSchema.newSchema()
            .query(GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                    .name("field")
                    .type(GraphQLString))
                .build())
            .additionalDirective(directive)
            .build()
        def document = new Parser().parseDocument('{ field @myDirective }')
        when:
        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)
        then:
        !errors.any { it.validationErrorType == ValidationErrorType.MissingDirectiveArgument }
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
