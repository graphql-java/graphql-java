package graphql

import graphql.parser.InvalidSyntaxException
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

/**
 * We trust that other unit tests of the parser and validation catch ALL of the combinations.  These tests
 * just show the combination of parsing and validation.
 */
class ParseAndValidateTest extends Specification {

    def "can parse a basic document"() {

        def input = ExecutionInput.newExecutionInput("query { hi }").variables([var1: 1]).build()

        when:
        def result = ParseAndValidate.parse(input)
        then:
        !result.isFailure()
        result.errors.isEmpty()
        result.document != null
        result.variables == [var1: 1]
        result.syntaxException == null
    }

    def "will pick up invalid documents"() {

        def input = ExecutionInput.newExecutionInput("query { hi( ").variables([var1: 1]).build()

        when:
        def result = ParseAndValidate.parse(input)
        then:
        result.isFailure()
        !result.errors.isEmpty()
        result.document == null
        result.variables == [var1: 1]
        result.syntaxException instanceof InvalidSyntaxException
    }

    def "will validate documents with no problems"() {

        def input = ExecutionInput.newExecutionInput("query { hero { name }}").variables([var1: 1]).build()
        def result = ParseAndValidate.parse(input)

        when:
        def errors = ParseAndValidate.validate(StarWarsSchema.starWarsSchema, result.getDocument())

        then:
        errors.isEmpty()
    }

    def "will validate documents with actual problems"() {

        def input = ExecutionInput.newExecutionInput("query { hero }").variables([var1: 1]).build()
        def result = ParseAndValidate.parse(input)

        when:
        def errors = ParseAndValidate.validate(StarWarsSchema.starWarsSchema, result.getDocument())

        then:
        !errors.isEmpty()
        errors[0].validationErrorType == ValidationErrorType.SubSelectionRequired
    }

    def "can combine parse and validation on valid input"() {
        def input = ExecutionInput.newExecutionInput("query { hero { name }}").variables([var1: 1]).build()

        when:
        def result = ParseAndValidate.parseAndValidate(StarWarsSchema.starWarsSchema, input)
        then:
        !result.isFailure()
        result.errors.isEmpty()
        result.validationErrors.isEmpty()
        result.document != null
        result.variables == [var1: 1]
        result.syntaxException == null
    }

    def "can combine parse and validation on VALID syntax but INVALID semantics"() {
        def input = ExecutionInput.newExecutionInput("query { hero }").variables([var1: 1]).build()

        when:
        def result = ParseAndValidate.parseAndValidate(StarWarsSchema.starWarsSchema, input)
        then:
        result.isFailure()
        !result.errors.isEmpty()
        !result.validationErrors.isEmpty()
        result.document != null
        result.variables == [var1: 1]
        result.syntaxException == null

        (result.errors[0] as ValidationError).validationErrorType == ValidationErrorType.SubSelectionRequired
    }

    def "can shortcut on parse and validation on INVALID syntax"() {
        def input = ExecutionInput.newExecutionInput("query { hero(").variables([var1: 1]).build()

        when:
        def result = ParseAndValidate.parseAndValidate(StarWarsSchema.starWarsSchema, input)
        then:
        result.isFailure()
        !result.errors.isEmpty()
        result.validationErrors.isEmpty()
        result.document == null
        result.variables == [var1: 1]
        result.syntaxException != null

        (result.errors[0] as InvalidSyntaxError).message.contains("Invalid Syntax")
    }
}
