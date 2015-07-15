package graphql.validation.rules

import graphql.language.BooleanValue
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.schema.GraphQLNonNull
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class DefaultValuesOfCorrectTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ErrorCollector errorCollector = new ErrorCollector()
    DefaultValuesOfCorrectType defaultValuesOfCorrectType = new DefaultValuesOfCorrectType(validationContext, errorCollector)

    def "NonNull type, but with default value"() {
        given:
        GraphQLNonNull nonNullType = new GraphQLNonNull(GraphQLString)
        StringValue defaultValue = new StringValue("string")
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("String"), defaultValue)
        validationContext.getInputType() >> nonNullType
        when:
        defaultValuesOfCorrectType.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsError(ValidationErrorType.DefaultForNonNullArgument)

    }

    def "default value has wrong type"(){
        given:
        BooleanValue defaultValue = new BooleanValue(false)
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("String"), defaultValue)
        validationContext.getInputType() >> GraphQLString
        when:
        defaultValuesOfCorrectType.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsError(ValidationErrorType.BadValueForDefaultArg)
    }
}
