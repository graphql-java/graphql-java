package graphql.validation.rules

import graphql.language.Argument
import graphql.language.StringValue
import graphql.schema.GraphQLFieldDefinition
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification


class KnownArgumentNamesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext, errorCollector)

    def "unknown argument"(){
        given:
        Argument argument = new Argument("unknownArg",new StringValue("value"))
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").build();
        validationContext.getFieldDef() >> fieldDefinition
        when:
        knownArgumentNames.checkArgument(argument)
        then:
        errorCollector.containsError(ValidationErrorType.UnknownArgument)
    }
}
