package graphql.validation.rules

import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.VariableReference
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLBigDecimal
import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.starWarsSchema

class ArgumentsOfCorrectTypeTest extends Specification {

    ArgumentsOfCorrectType argumentsOfCorrectType
    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def setup() {
        argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, errorCollector)
    }

    def "valid type results in no error"() {
        given:
        def variableReference = new VariableReference("ref")
        def argumentLiteral = new Argument("arg", variableReference)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLBigDecimal)
        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.errors.isEmpty()
    }

    def "invalid type results in error"() {
        given:
        def stringValue = new StringValue("string")
        def argumentLiteral = new Argument("arg", stringValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLBoolean)
        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg' with value 'StringValue{value='string'}' is not a valid 'Boolean'"
    }

    def "invalid input object type results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string"))])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLInputObjectType.newInputObject().name("ArgumentObjectType").field(GraphQLInputObjectField.newInputObjectField().name("foo").type(GraphQLBoolean)).build())

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg.foo' with value 'StringValue{value='string'}' is not a valid 'Boolean'"
    }

    def "invalid list object type results in error"() {
        given:

        def validValue = new ObjectValue([new ObjectField("foo", new BooleanValue(true))])
        def invalidValue = new ObjectValue([new ObjectField("foo", new StringValue("string"))])
        def arrayValue = new ArrayValue([validValue, invalidValue])
        def argumentLiteral = new Argument("arg", arrayValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLList.list(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType").field(GraphQLInputObjectField.newInputObjectField().name("foo").type(GraphQLBoolean)).build()))

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg[1].foo' with value 'StringValue{value='string'}' is not a valid 'Boolean'"
    }

    def "invalid list inside object type results in error"() {
        given:

        def validValue = new ObjectValue([new ObjectField("foo", new ArrayValue([new BooleanValue(true), new BooleanValue(false)]))])
        def invalidValue = new ObjectValue([new ObjectField("foo", new ArrayValue([new BooleanValue(true), new StringValue('string')]))])
        def arrayValue = new ArrayValue([invalidValue, validValue])
        def argumentLiteral = new Argument("arg", arrayValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLList.list(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType").field(GraphQLInputObjectField.newInputObjectField().name("foo").type(GraphQLList.list(GraphQLBoolean))).build()))

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg[0].foo[1]' with value 'StringValue{value='string'}' is not a valid 'Boolean'"
    }

    def "invalid list simple type results in error"() {
        given:

        def validValue = new BooleanValue(true)
        def invalidValue = new StringValue("string")
        def arrayValue = new ArrayValue([validValue, invalidValue])
        def argumentLiteral = new Argument("arg", arrayValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLList.list(GraphQLBoolean))

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg[1]' with value 'StringValue{value='string'}' is not a valid 'Boolean'"
    }

    def "type missing fields results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string"))])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build())

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg' with value 'ObjectValue{objectFields=[ObjectField{name='foo', value=StringValue{value='string'}}]}' is missing required fields '[bar]'"
    }

    def "type not object results in error"() {
        given:
        def objectValue = new StringValue("string")
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build())

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg' with value 'StringValue{value='string'}' must be an object type"
    }

    def "type null fields results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string")), new ObjectField("bar", NullValue.Null)])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build())

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg.bar' with value 'NullValue{}' must not be null"
    }

    def "type with extra fields results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string")), new ObjectField("bar", new StringValue("string")), new ObjectField("fooBar", new BooleanValue(true))])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = new GraphQLArgument("arg", GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build())

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
        errorCollector.errors[0].message == "Validation error of type WrongType: argument 'arg' with value 'ObjectValue{objectFields=[ObjectField{name='foo', value=StringValue{value='string'}}, ObjectField{name='bar', value=StringValue{value='string'}}, ObjectField{name='fooBar', value=BooleanValue{value=true}}]}' contains a field not in 'ArgumentObjectType': 'fooBar'"
    }

    def "current null argument from context is no error"() {
        given:
        def stringValue = new StringValue("string")
        def argumentLiteral = new Argument("arg", stringValue)
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        argumentsOfCorrectType.getErrors().isEmpty()
    }
}
