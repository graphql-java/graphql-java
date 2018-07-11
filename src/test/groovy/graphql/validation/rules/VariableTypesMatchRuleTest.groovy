package graphql.validation.rules

import graphql.Scalars
import graphql.StarWarsSchema
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class VariableTypesMatchRuleTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    VariableTypesMatchRule variableTypesMatchRule
    VariablesTypesMatcher variablesTypeMatcher

    def setup() {
        variablesTypeMatcher = Mock(VariablesTypesMatcher)
        variableTypesMatchRule = new VariableTypesMatchRule(validationContext, errorCollector, variablesTypeMatcher)
    }

    def "invalid type"() {
        given:
        def defaultValue = new StringValue("default")
        def astType = new TypeName("String")
        def expectedType = Scalars.GraphQLBoolean

        validationContext.getSchema() >> StarWarsSchema.starWarsSchema
        validationContext.getInputType() >> expectedType
        variablesTypeMatcher.effectiveType(Scalars.GraphQLString, defaultValue) >> Scalars.GraphQLString
        variablesTypeMatcher
                .doesVariableTypesMatch(Scalars.GraphQLString, defaultValue, expectedType) >> false

        when:
        variableTypesMatchRule.checkOperationDefinition(OperationDefinition.newOperationDefinition().build())
        variableTypesMatchRule.checkVariableDefinition(new VariableDefinition("var", astType, defaultValue))
        variableTypesMatchRule.checkVariable(new VariableReference("var"))

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
    }

    def "validation error message contains type information - issue 911 "() {
        given:
        def defaultValue = null
        def astType = new NonNullType(new ListType(new TypeName("String")))
        def expectedType = GraphQLList.list(GraphQLNonNull.nonNull(Scalars.GraphQLString))

        def mismatchedType = GraphQLNonNull.nonNull(GraphQLList.list(Scalars.GraphQLString))

        validationContext.getSchema() >> StarWarsSchema.starWarsSchema
        validationContext.getInputType() >> expectedType
        variablesTypeMatcher.effectiveType(mismatchedType, defaultValue) >> mismatchedType
        variablesTypeMatcher
                .doesVariableTypesMatch(expectedType, defaultValue, expectedType) >> false


        when:
        variableTypesMatchRule.checkOperationDefinition(OperationDefinition.newOperationDefinition().build())
        variableTypesMatchRule.checkVariableDefinition(new VariableDefinition("var", astType, defaultValue))
        variableTypesMatchRule.checkVariable(new VariableReference("var"))

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message.contains("Variable type '[String]!' doesn't match expected type '[String!]'")
    }
}
