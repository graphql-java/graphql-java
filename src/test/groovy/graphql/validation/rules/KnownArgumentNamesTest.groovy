package graphql.validation.rules

import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.StringValue
import graphql.parser.Parser
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString

class KnownArgumentNamesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext, errorCollector)

    def setup() {
        validationContext.i18n(_, _) >> "test error message"
    }

    def "unknown field argument"() {
        given:
        Argument argument = Argument.newArgument("unknownArg", StringValue.newStringValue("value").build()).build()
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString)
                .argument(GraphQLArgument.newArgument().name("knownArg").type(GraphQLString).build()).build()
        validationContext.getFieldDef() >> fieldDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownArgument)
    }

    def "known field argument"() {
        given:
        Argument argument = Argument.newArgument("knownArg", StringValue.newStringValue("value").build()).build()
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString)
                .argument(GraphQLArgument.newArgument().name("knownArg").type(GraphQLString).build()).build()
        validationContext.getFieldDef() >> fieldDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.errors.isEmpty()
    }

    def "unknown directive argument"() {
        given:
        Argument argument = Argument.newArgument("unknownArg", BooleanValue.newBooleanValue(true).build()).build()
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build()
        def directiveDefinition = GraphQLDirective.newDirective()
                .name("directive")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(GraphQLArgument.newArgument().name("knownArg").type(GraphQLBoolean).build()).build()
        validationContext.getFieldDef() >> fieldDefinition
        validationContext.getDirective() >> directiveDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownDirective)
    }

    def "known directive argument"() {
        given:
        Argument argument = Argument.newArgument("knownArg", BooleanValue.newBooleanValue(true).build()).build()
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build()
        def directiveDefinition = GraphQLDirective.newDirective()
                .name("directive")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(GraphQLArgument.newArgument().name("knownArg").type(GraphQLBoolean).build()).build()
        validationContext.getFieldDef() >> fieldDefinition
        validationContext.getDirective() >> directiveDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.errors.isEmpty()
    }

    def "directive argument not validated against field arguments"() {
        given:
        Argument argument = Argument.newArgument("unknownArg", BooleanValue.newBooleanValue(true).build()).build()
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString)
                .argument(GraphQLArgument.newArgument().name("unknownArg").type(GraphQLString).build()).build()
        def directiveDefinition = GraphQLDirective.newDirective()
                .name("directive")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(GraphQLArgument.newArgument().name("knownArg").type(GraphQLBoolean).build()).build()
        validationContext.getFieldDef() >> fieldDefinition
        validationContext.getDirective() >> directiveDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownDirective)
    }

    def "directive missing argument validation error with message"() {
        def query = """
            query getDogName {
              dog @dogDirective(notArgument: "value"){
                  name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownDirective
        validationErrors.get(0).message == "Validation error (UnknownDirective@[dog]) : Unknown directive argument 'notArgument'"
    }

    def "field missing argument validation error with message"() {
        def query = """
            query getDog {
              dog {
                  doesKnowCommand(dogCommand: SIT, notArgument: false)
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownArgument
        validationErrors.get(0).message == "Validation error (UnknownArgument@[dog/doesKnowCommand]) : Unknown field argument 'notArgument'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
