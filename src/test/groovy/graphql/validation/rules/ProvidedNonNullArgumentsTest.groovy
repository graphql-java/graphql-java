package graphql.validation.rules

import graphql.language.Argument
import graphql.language.Directive
import graphql.language.Field
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.StringValue
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class ProvidedNonNullArgumentsTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ProvidedNonNullArguments providedNonNullArguments = new ProvidedNonNullArguments(validationContext, errorCollector)

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

    def "not provided and but defaulted non null field argument"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg")
                .type(GraphQLNonNull.nonNull(GraphQLString))
                .defaultValue("defaultVal")
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
                .argument(directiveArg)
                .build()
        validationContext.getDirective() >> graphQLDirective

        def directive = new Directive("directive")

        when:
        providedNonNullArguments.checkDirective(directive, [])

        then:
        errorCollector.containsValidationError(ValidationErrorType.MissingDirectiveArgument)
    }

    def "not provided but defaulted directive argument"() {
        given:
        def directiveArg = GraphQLArgument.newArgument()
                .name("arg").type(GraphQLNonNull.nonNull(GraphQLString))
                .defaultValue("defaultVal")
        def graphQLDirective = GraphQLDirective.newDirective()
                .name("directive")
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
}
