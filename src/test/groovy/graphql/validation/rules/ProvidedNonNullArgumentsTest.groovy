package graphql.validation.rules

import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.Field
import graphql.language.NullValue
import graphql.language.StringValue
import graphql.parser.Parser
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class ProvidedNonNullArgumentsTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ProvidedNonNullArguments providedNonNullArguments = new ProvidedNonNullArguments(validationContext, errorCollector)

    def setup() {
        validationContext.i18n(_, _) >> "test error message"
    }

    def "not provided and not defaulted non null field argument"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg")
                .type(GraphQLNonNull.nonNull(GraphQLString))
        def fieldDef = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(GraphQLString)
                .argument(fieldArg)
                .build()
        validationContext.getFieldDef() >> fieldDef

        def field = new Field("field")

        when:
        providedNonNullArguments.checkField(field)

        then:
        errorCollector.containsValidationError(ValidationErrorType.MissingFieldArgument)
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

    def "not provided and but defaulted non null field argument"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg")
                .type(GraphQLNonNull.nonNull(GraphQLString))
                .defaultValueProgrammatic("defaultVal")
        def fieldDef = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(GraphQLString)
                .argument(fieldArg)
                .build()
        validationContext.getFieldDef() >> fieldDef

        def field = new Field("field")

        when:
        providedNonNullArguments.checkField(field)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def "all field arguments are provided"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg")
                .type(GraphQLNonNull.nonNull(GraphQLString))
        def fieldDef = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(GraphQLString)
                .argument(fieldArg)
                .build()
        validationContext.getFieldDef() >> fieldDef

        def field = new Field("field", [new Argument("arg", new StringValue("hallo"))])

        when:
        providedNonNullArguments.checkField(field)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def "not provided not defaulted directive argument"() {
        given:
        def directiveArg = GraphQLArgument.newArgument()
                .name("arg").type(GraphQLNonNull.nonNull(GraphQLString))
        def graphQLDirective = GraphQLDirective.newDirective()
                .name("directive")
                .validLocation(Introspection.DirectiveLocation.SCALAR)
                .argument(directiveArg)
                .build()
        validationContext.getDirective() >> graphQLDirective

        def directive = new Directive("directive")

        when:
        providedNonNullArguments.checkDirective(directive, [])

        then:
        errorCollector.containsValidationError(ValidationErrorType.MissingDirectiveArgument)
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

    def "not provided but defaulted directive argument"() {
        given:
        def directiveArg = GraphQLArgument.newArgument()
                .name("arg").type(GraphQLNonNull.nonNull(GraphQLString))
                .defaultValueProgrammatic("defaultVal")
        def graphQLDirective = GraphQLDirective.newDirective()
                .name("directive")
                .validLocation(Introspection.DirectiveLocation.SCALAR)
                .argument(directiveArg)
                .build()
        validationContext.getDirective() >> graphQLDirective

        def directive = new Directive("directive")

        when:
        providedNonNullArguments.checkDirective(directive, [])

        then:
        errorCollector.getErrors().isEmpty()
    }

    def "all directive arguments are provided"() {
        given:
        def directiveArg = GraphQLArgument.newArgument().name("arg").type(GraphQLNonNull.nonNull(GraphQLString))
        def graphQLDirective = GraphQLDirective.newDirective()
                .name("directive")
                .validLocation(Introspection.DirectiveLocation.SCALAR)
                .argument(directiveArg)
                .build()
        validationContext.getDirective() >> graphQLDirective

        def directive = new Directive("directive", [new Argument("arg", new StringValue("hallo"))])


        when:
        providedNonNullArguments.checkDirective(directive, [])

        then:
        errorCollector.getErrors().isEmpty()
    }

    def "provide the explicit value null is not valid for non null argument"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg")
                .type(GraphQLNonNull.nonNull(GraphQLString))

        def fieldDef = GraphQLFieldDefinition.newFieldDefinition()
                .name("field")
                .type(GraphQLString)
                .argument(fieldArg)
                .build()

        validationContext.getFieldDef() >> fieldDef

        def defaultNullArg = Argument.newArgument().name("arg").value(NullValue.newNullValue().build()).build()
        def field = new Field("field", [defaultNullArg])

        when:
        providedNonNullArguments.checkField(field)

        then:
        errorCollector.containsValidationError(ValidationErrorType.NullValueForNonNullArgument)
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

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
