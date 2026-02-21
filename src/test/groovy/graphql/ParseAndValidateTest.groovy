package graphql

import graphql.language.Document
import graphql.language.SourceLocation
import graphql.parser.InvalidSyntaxException
import graphql.parser.Parser
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.UnExecutableSchemaGenerator
import graphql.validation.OperationValidationRule
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import java.util.function.Predicate

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
        def errors = ParseAndValidate.validate(StarWarsSchema.starWarsSchema, result.getDocument(), input.getLocale())

        then:
        errors.isEmpty()
    }

    def "will validate documents with actual problems"() {

        def input = ExecutionInput.newExecutionInput("query { hero }").variables([var1: 1]).build()
        def result = ParseAndValidate.parse(input)

        when:
        def errors = ParseAndValidate.validate(StarWarsSchema.starWarsSchema, result.getDocument(), input.getLocale())

        then:
        !errors.isEmpty()
        errors[0].validationErrorType == ValidationErrorType.SubselectionRequired
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

        (result.errors[0] as ValidationError).validationErrorType == ValidationErrorType.SubselectionRequired
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

        (result.errors[0] as InvalidSyntaxError).message.contains("Invalid syntax")
    }

    def "can use the graphql context to stop certain validation rules"() {

        def sdl = '''type Query { foo : ID } '''
        def graphQL = TestUtil.graphQL(sdl).build()

        Predicate<OperationValidationRule> predicate = new Predicate<OperationValidationRule>() {
            @Override
            boolean test(OperationValidationRule rule) {
                if (rule == OperationValidationRule.NO_UNUSED_FRAGMENTS) {
                    return false
                }
                return true
            }
        }

        def query = '''
            query { foo }

            fragment UnusedFrag on Query {
                foo
            }
        '''

        when:
        def ei = ExecutionInput.newExecutionInput(query)
                .graphQLContext(["graphql.ParseAndValidate.Predicate": predicate])
                .build()
        def rs = graphQL.execute(ei)

        then:
        rs.errors.isEmpty() // we skipped a rule

        when:
        predicate = { it -> true }
        ei = ExecutionInput.newExecutionInput(query)
                .graphQLContext(["graphql.ParseAndValidate.Predicate": predicate])
                .build()
        rs = graphQL.execute(ei)

        then:
        !rs.errors.isEmpty() // all rules apply - we have errors
    }

    def "validation error raised if mutation operation does not exist in schema"() {
        def sdl = '''
        type Query {
            myQuery : String!
        }
        '''

        def registry = new SchemaParser().parse(sdl)
        def schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        String request = "mutation MyMutation { myMutation }"

        when:
        Document inputDocument = new Parser().parseDocument(request)
        List<ValidationError> errors = ParseAndValidate.validate(schema, inputDocument)

        then:
        errors.size() == 1
        def error = errors.first()
        error.validationErrorType == ValidationErrorType.UnknownOperation
        error.message == "Validation error (UnknownOperation): The 'Mutation' operation is not supported by the schema"
        error.locations == [new SourceLocation(1, 1)]
    }

    def "validation error raised if subscription operation does not exist in schema"() {
        def sdl = '''
        type Query {
            myQuery : String!
        }
        '''

        def registry = new SchemaParser().parse(sdl)
        def schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)

        String request = "subscription MySubscription { mySubscription }"

        when:
        Document inputDocument = new Parser().parseDocument(request)
        List<ValidationError> errors = ParseAndValidate.validate(schema, inputDocument)

        then:
        errors.size() == 1
        def error = errors.first()
        error.validationErrorType == ValidationErrorType.UnknownOperation
        error.message == "Validation error (UnknownOperation): The 'Subscription' operation is not supported by the schema"
        error.locations == [new SourceLocation(1, 1)]
    }

    def "known operation validation rule checks all operations in document"() {
        def sdl = '''
        type Query {
            myQuery : String!
        }
        '''

        def registry = new SchemaParser().parse(sdl)
        def schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        String request = "mutation MyMutation { myMutation } subscription MySubscription { mySubscription }"

        when:
        Document inputDocument = new Parser().parseDocument(request)
        List<ValidationError> errors = ParseAndValidate.validate(schema, inputDocument)

        then:
        errors.size() == 2
        def error1 = errors.get(0)
        error1.validationErrorType == ValidationErrorType.UnknownOperation
        error1.message == "Validation error (UnknownOperation): The 'Mutation' operation is not supported by the schema"
        error1.locations == [new SourceLocation(1, 1)]

        def error2 = errors.get(1)
        error2.validationErrorType == ValidationErrorType.UnknownOperation
        error2.message == "Validation error (UnknownOperation): The 'Subscription' operation is not supported by the schema"
        error2.locations == [new SourceLocation(1, 36)]
    }
}
