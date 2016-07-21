package graphql.validation.rules

import graphql.language.Argument
import graphql.language.Field
import graphql.language.StringValue
import graphql.schema.GraphQLFieldDefinition
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class KnownArgumentNamesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    KnownArgumentNames knownArgumentNames = new KnownArgumentNames(validationContext, errorCollector)

    def "unknown argument"(){
        given:
        Argument argument = new Argument("unknownArg",new StringValue("value"))
        Field field = new Field("f", [argument])
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build();
        validationContext.getFieldDef() >> fieldDefinition
        when:
        knownArgumentNames.checkField(field)
        then:
        errorCollector.containsValidationError(ValidationErrorType.UnknownArgument)
    }
}
