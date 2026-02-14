package graphql.validation.rules

import graphql.GraphQLContext
import graphql.i18n.I18n
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.VariableReference
import graphql.parser.Parser
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.starWarsSchema

class ArgumentsOfCorrectTypeTest extends Specification {

    ArgumentsOfCorrectType argumentsOfCorrectType
    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    I18n i18n = Mock(I18n)

    def setup() {
        argumentsOfCorrectType = new ArgumentsOfCorrectType(validationContext, errorCollector)
        def context = GraphQLContext.getDefault()
        validationContext.getGraphQLContext() >> context
        validationContext.getI18n() >> i18n
        validationContext.i18n(_, _) >> "test error message"
        i18n.getLocale() >> Locale.ENGLISH
    }

    def "error message uses locale of client (German), not server (English)"() {
        def query = """
            query getDog {
              dog @objectArgumentDirective(myObject: { id: "1" }) {
                name
              }           
            }
        """
        def document = new Parser().parseDocument(query)

        when:
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.GERMAN)

        then:
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validierungsfehler (WrongType@[dog]) : Argument 'myObject' mit Wert 'ObjectValue{objectFields=[ObjectField{name='id', value=StringValue{value='1'}}]}' fehlen Pflichtfelder '[name]'"
    }

    def "valid type results in no error"() {
        given:
        def variableReference = new VariableReference("ref")
        def argumentLiteral = new Argument("arg", variableReference)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLInt).build()
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
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLBoolean).build()
        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "invalid type scalar results in error with message"() {
        def query = """
            query getDog {
              dog(arg1: 1) {
                name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[dog]) : argument 'arg1' with value 'IntValue{value=1}' is not a valid 'String' - Expected an AST type of 'StringValue' but it was a 'IntValue'"
    }

    def "invalid input object type results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string"))])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType").field(GraphQLInputObjectField.newInputObjectField().name("foo").type(GraphQLBoolean)).build()).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "invalid list object type results in error"() {
        given:

        def validValue = new ObjectValue([new ObjectField("foo", new BooleanValue(true))])
        def invalidValue = new ObjectValue([new ObjectField("foo", new StringValue("string"))])
        def arrayValue = new ArrayValue([validValue, invalidValue])
        def argumentLiteral = new Argument("arg", arrayValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLList.list(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType").field(GraphQLInputObjectField.newInputObjectField().name("foo").type(GraphQLBoolean)).build())).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "invalid list inside object type results in error"() {
        given:

        def validValue = new ObjectValue([new ObjectField("foo", new ArrayValue([new BooleanValue(true), new BooleanValue(false)]))])
        def invalidValue = new ObjectValue([new ObjectField("foo", new ArrayValue([new BooleanValue(true), new StringValue('string')]))])
        def arrayValue = new ArrayValue([invalidValue, validValue])
        def argumentLiteral = new Argument("arg", arrayValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLList.list(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType").field(GraphQLInputObjectField.newInputObjectField().name("foo").type(GraphQLList.list(GraphQLBoolean))).build())).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "invalid list simple type results in error"() {
        given:

        def validValue = new BooleanValue(true)
        def invalidValue = new StringValue("string")
        def arrayValue = new ArrayValue([validValue, invalidValue])
        def argumentLiteral = new Argument("arg", arrayValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLList.list(GraphQLBoolean)).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "type missing fields results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string"))])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "type missing fields results in error with message"() {
        def query = """
            query getDog {
              dog @objectArgumentDirective(myObject: { id: "1" }) {
                name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[dog]) : argument 'myObject' with value 'ObjectValue{objectFields=[ObjectField{name='id', value=StringValue{value='1'}}]}' is missing required fields '[name]'"
    }

    def "type not object results in error"() {
        given:
        def objectValue = new StringValue("string")
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "invalid not object type results in error with message"() {
        def query = """
            query getDog {
              dog @objectArgumentDirective(myObject: 1) {
                name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[dog]) : argument 'myObject' with value 'IntValue{value=1}' must be an object type"
    }

    def "type null fields results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string")), new ObjectField("bar", NullValue.newNullValue().build())])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "type null results in error with message"() {
        def query = """
            query getDog {
              dog {
                  doesKnowCommand(dogCommand: null)
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2 // First error is NullValueForNonNullArgument
        validationErrors.get(1).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(1).message == "Validation error (WrongType@[dog/doesKnowCommand]) : argument 'dogCommand' with value 'NullValue{}' must not be null"
    }

    def "type with extra fields results in error"() {
        given:
        def objectValue = new ObjectValue([new ObjectField("foo", new StringValue("string")), new ObjectField("bar", new StringValue("string")), new ObjectField("fooBar", new BooleanValue(true))])
        def argumentLiteral = new Argument("arg", objectValue)
        def graphQLArgument = GraphQLArgument.newArgument().name("arg").type(GraphQLInputObjectType.newInputObject().name("ArgumentObjectType")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("foo").type(GraphQLNonNull.nonNull(GraphQLString)))
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("bar").type(GraphQLNonNull.nonNull(GraphQLString)))
                .build()).build()

        argumentsOfCorrectType.validationContext.getArgument() >> graphQLArgument
        argumentsOfCorrectType.validationContext.getSchema() >> starWarsSchema

        when:
        argumentsOfCorrectType.checkArgument(argumentLiteral)
        then:
        errorCollector.containsValidationError(ValidationErrorType.WrongType)
        errorCollector.errors.size() == 1
    }

    def "type with extra fields results in error with message"() {
        def query = """
            query getDog {
              dog @objectArgumentDirective(myObject: { name: "Gary", extraField: "ShouldNotBeHere" }) {
                name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[dog]) : argument 'myObject' with value 'ObjectValue{objectFields=[ObjectField{name='name', value=StringValue{value='Gary'}}, ObjectField{name='extraField', value=StringValue{value='ShouldNotBeHere'}}]}' contains a field not in 'Input': 'extraField'"
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

    def "invalid enum type results in error with message"() {
        def query = """
            query getDog {
              dog {
                  doesKnowCommand(dogCommand: PRETTY)
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[dog/doesKnowCommand]) : argument 'dogCommand' with value 'EnumValue{name='PRETTY'}' is not a valid 'DogCommand' - Literal value not in allowable values for enum 'DogCommand' - 'EnumValue{name='PRETTY'}'"
    }

    def "invalid @oneOf argument - has more than 1 key - case #why"() {
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.WrongType
        validationErrors.get(0).message == "Validation error (WrongType@[oneOfField]) : Exactly one key must be specified for OneOf type 'oneOfInputType'."

        where:
        why              | query                                             | _
        'some variables' |
                '''
            query q($v1 : String) {
              oneOfField(oneOfArg : { a : $v1, b : "y" })
            }
        '''                                               | _
        'all variables'  |
                '''
            query q($v1 : String, $v2 : String) {
              oneOfField(oneOfArg : { a : $v1, b : $v2 })
            }
        '''                                               | _
        'all literals'   |
                '''
            query q {
              oneOfField(oneOfArg : { a : "x", b : "y" })
            }
        '''                                               | _
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
