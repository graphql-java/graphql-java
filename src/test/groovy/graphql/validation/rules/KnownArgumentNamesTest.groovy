package graphql.validation.rules

import graphql.Directives
import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.StringValue
import graphql.schema.GraphQLFieldDefinition
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument

class KnownArgumentNamesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext, errorCollector)


    def "unknown argument"(){
        given:
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build();
        Argument argument = new Argument("unknownArg",new StringValue("value"))
        validationContext.getFieldDef() >> fieldDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownArgument)
    }

    def "directives"() {
        given:
        def fieldArg = newArgument().name("arg").type(GraphQLString)
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").argument(fieldArg).type(GraphQLString).build();
        def directive = Directives.IncludeDirective
        validationContext.getFieldDef() >> fieldDefinition
        validationContext.getDirective() >> directive
        def argument = new Argument("if", new BooleanValue(true))
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        !errorCollector.containsValidationError(ValidationErrorType.UnknownArgument)
    }
}
