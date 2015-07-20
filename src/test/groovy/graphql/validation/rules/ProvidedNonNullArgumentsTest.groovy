package graphql.validation.rules

import graphql.language.Argument
import graphql.language.Directive
import graphql.language.Field
import graphql.language.StringValue
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class ProvidedNonNullArgumentsTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    ProvidedNonNullArguments providedNonNullArguments = new ProvidedNonNullArguments(validationContext, errorCollector)

    def "not provided field argument"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg").type(new GraphQLNonNull(GraphQLString)).build()
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
        errorCollector.containsError(ValidationErrorType.MissingFieldArgument)
    }


    def "all field arguments are provided"() {
        given:
        def fieldArg = GraphQLArgument.newArgument().name("arg").type(new GraphQLNonNull(GraphQLString)).build()
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

    def "not provided directive argument"() {
        given:
        def directiveArg = GraphQLArgument.newArgument().name("arg").type(new GraphQLNonNull(GraphQLString)).build()
        def graphQLDirective = GraphQLDirective.newDirective()
                .name("directive")
                .argument(directiveArg)
                .build()
        validationContext.getDirective() >> graphQLDirective

        def directive = new Directive("directive")

        when:
        providedNonNullArguments.checkDirective(directive)

        then:
        errorCollector.containsError(ValidationErrorType.MissingDirectiveArgument)
    }


    def "all directive arguments are provided"() {
        given:
        def directiveArg = GraphQLArgument.newArgument().name("arg").type(new GraphQLNonNull(GraphQLString)).build()
        def graphQLDirective = GraphQLDirective.newDirective()
                .name("directive")
                .argument(directiveArg)
                .build()
        validationContext.getDirective() >> graphQLDirective

        def directive = new Directive("directive", [new Argument("arg", new StringValue("hallo"))])


        when:
        providedNonNullArguments.checkDirective(directive)

        then:
        errorCollector.getErrors().isEmpty()
    }
}
