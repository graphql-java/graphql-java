package graphql.validation

import graphql.TestUtil
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class ArgumentsOfCorrectTypeTest extends Specification {

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
        def query = """
            query getDog(\$cmd: DogCommand!) {
              dog {
                doesKnowCommand(dogCommand: \$cmd)
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def "invalid type results in error"() {
        def query = """
            query getDog {
              dog {
                doesKnowCommand(dogCommand: "notAnEnum")
              }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.any { it.validationErrorType == ValidationErrorType.WrongType }
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

    def "invalid input object field type results in error"() {
        def schema = TestUtil.schema("""
            type Query { field(arg: TestInput): String }
            input TestInput { flag: Boolean }
        """)
        def document = new Parser().parseDocument('{ field(arg: { flag: "notABoolean" }) }')
        when:
        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)
        then:
        errors.any { it.validationErrorType == ValidationErrorType.WrongType }
    }

    def "invalid list of input objects results in error"() {
        def schema = TestUtil.schema("""
            type Query { field(arg: [TestInput]): String }
            input TestInput { flag: Boolean }
        """)
        def document = new Parser().parseDocument('{ field(arg: [{ flag: true }, { flag: "wrong" }]) }')
        when:
        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)
        then:
        errors.any { it.validationErrorType == ValidationErrorType.WrongType }
    }

    def "invalid nested list inside input object results in error"() {
        def schema = TestUtil.schema("""
            type Query { field(arg: [TestInput]): String }
            input TestInput { flags: [Boolean] }
        """)
        def document = new Parser().parseDocument('{ field(arg: [{ flags: [true, "wrong"] }]) }')
        when:
        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)
        then:
        errors.any { it.validationErrorType == ValidationErrorType.WrongType }
    }

    def "invalid simple list type results in error"() {
        def schema = TestUtil.schema("""
            type Query { field(arg: [Boolean]): String }
        """)
        def document = new Parser().parseDocument('{ field(arg: [true, "wrong"]) }')
        when:
        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)
        then:
        errors.any { it.validationErrorType == ValidationErrorType.WrongType }
    }

    def "null value for non-null field in input object results in error"() {
        def query = """
            query getDog {
              dog @objectArgumentDirective(myObject: { id: "1", name: null }) {
                name
              }
            }
        """
        when:
        def validationErrors = validate(query)
        then:
        validationErrors.any { it.validationErrorType == ValidationErrorType.WrongType }
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
