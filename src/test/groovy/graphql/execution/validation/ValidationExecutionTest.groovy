package graphql.execution.validation

import graphql.ErrorType
import graphql.GraphQLError
import graphql.TestUtil
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionPath
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentBuilder
import graphql.schema.GraphQLInputType
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import java.util.function.Function

import static graphql.ExecutionInput.newExecutionInput
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry

class ValidationExecutionTest extends Specification {

    def spec = '''

        type Query {
            walksIntoABar(barInput : BarInput, first : Int, last: Int) : String   
        }
        
        input BarInput {
            age : Int
            hasShoes : Boolean
        }
        
    '''

    static ValidationResult nonNull(Object value, ValidationRuleEnvironment environment, Function<ValidationRuleEnvironment, String> errorMsg) {
        ValidationResult.Builder result = ValidationResult.newResult()
        if (value == null) {
            GraphQLError graphQLError = environment.mkError(errorMsg.apply(environment))
            result.withErrors(graphQLError)
        }
        return result.continueIfNoErrors()
    }


    ValidationRule requiresFirstOrLast = new ValidationRule() {
        @Override
        ValidationResult validate(ValidationRuleEnvironment env) {
            def result = ValidationResult.newResult()
            if (!(env.containsArgument("first") || env.containsArgument("last"))) {
                result.withErrors(env.mkError("You must provide either the first or last argument"))
            }
            return result.continueIfNoErrors()
        }
    }

    ValidationRule barInputIsAllowed = new ValidationRule() {
        @Override
        ValidationResult validate(ValidationRuleEnvironment env) {
            def result = ValidationResult.newResult()
            def barInput = env.getValidatedArgumentValue()
            if (barInput != null) {
                result.withResult(nonNull(barInput['age'], env, { env2 -> "You must be over 18 to enter this bar" }))
                if (barInput['age'] < 18) {
                    result.withErrors(env.mkError("You must be over 18 to enter this bar"))
                }
                if (barInput['hasShoes'] != true) {
                    result.withErrors(env.mkError("You must be wearing shoes"))
                }
            }
            return result.continueIfNoErrors()
        }
    }

    class ValidationRuleWrapperWithInstruction implements ValidationRule {

        ValidationRule delegate;
        ValidationResult.Instruction instruction;

        ValidationRuleWrapperWithInstruction(ValidationRule delegate, ValidationResult.Instruction instruction) {
            this.delegate = delegate
            this.instruction = instruction
        }

        @Override
        ValidationResult validate(ValidationRuleEnvironment environment) {
            def interimResult = delegate.validate(environment)
            return ValidationResult.newResult().withResult(interimResult).instruction(instruction).build()
        }
    }


    private DataFetchingEnvironment buildDFE(List<ValidationRule> fieldRules, List<ValidationRule> typeRules, Map<String, Object> args) {
        def schema = TestUtil.schema(spec)
        def parentType = schema.getObjectType("Query")
        def fieldDefinition = parentType.getFieldDefinition("walksIntoABar")
        def field = new Field("walksIntoABar")
        def path = ExecutionPath.parse("/walksIntoABar")

        def inputType = schema.getType("BarInput") as GraphQLInputType

        def codeRegistry = newCodeRegistry()
                .fieldValidationRules(parentType, fieldDefinition, fieldRules)
                .inputTypeValidationRules(inputType, typeRules)
                .build()
        schema = schema.transform({ builder -> builder.codeRegistry(codeRegistry) })

        def ec = ExecutionContextBuilder.newExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .build()

        def stepInfo = newExecutionStepInfo()
                .type(fieldDefinition.getType())
                .fieldDefinition(fieldDefinition)
                .field(field)
                .path(path)
                .arguments(args)
                .build()


        def dfe = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment()
                .executionContext(ec)
                .graphQLSchema(schema)
                .parentType(parentType)
                .fieldDefinition(fieldDefinition)
                .arguments(args)
                .executionStepInfo(stepInfo)
                .fields([field])
                .build()
        dfe
    }


    def "field rules get found"() {
        def args = [:]
        DataFetchingEnvironment dfe = buildDFE([requiresFirstOrLast], [], args)

        ValidationExecution validationExecution = new ValidationExecution()
        when:
        def result = validationExecution.validateField(dfe)
        then:
        result.instruction == ValidationResult.Instruction.RETURN_NULL
        result.errors[0].message == "You must provide either the first or last argument"
        result.errors[0].path == ["walksIntoABar"]
        result.errors[0].errorType == ErrorType.ValidationError
    }

    def "input type rules get found"() {
        def args = [barInput: [age: 14, hasShoes: true]]
        DataFetchingEnvironment dfe = buildDFE([], [barInputIsAllowed], args)

        ValidationExecution validationExecution = new ValidationExecution()
        when:
        def result = validationExecution.validateField(dfe)
        then:
        result.instruction == ValidationResult.Instruction.RETURN_NULL
        result.errors.size() == 1
        result.errors[0].message == "You must be over 18 to enter this bar"
        result.errors[0].path == ["walksIntoABar"]
        result.errors[0].errorType == ErrorType.ValidationError

        when:

        args = [barInput: [age: 18, hasShoes: true]]
        dfe = buildDFE([], [barInputIsAllowed], args)

        result = validationExecution.validateField(dfe)

        then:
        result.instruction == ValidationResult.Instruction.CONTINUE_FETCHING
        result.errors.isEmpty()
    }


    ValidationRule continueOn(ValidationRule validationRule) {
        new ValidationRuleWrapperWithInstruction(validationRule, ValidationResult.Instruction.CONTINUE_FETCHING)
    }

    def "integration test of rules running that returns null and hence stops fetches"() {

        DataFetcher df = { env -> "beer" }

        def codeRegistry = newCodeRegistry()
                .dataFetcher("Query", "walksIntoABar", df)
                .fieldValidationRules("Query", "walksIntoABar", requiresFirstOrLast)
                .build()
        def runtimeWiring = RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistry).build()

        def graphql = TestUtil.graphQL(spec, runtimeWiring).build()
        when:
        def result = graphql.execute(newExecutionInput().
                query('''
                    query {
                        walksIntoABar
                    }
                '''))
        then:
        result.data["walksIntoABar"] == null // set to null via rule
        result.errors[0].message == "You must provide either the first or last argument"
        result.errors[0].path == ["walksIntoABar"]
        result.errors[0].errorType == ErrorType.ValidationError
    }

    def "integration test of rules running that returns continue"() {

        DataFetcher df = { env -> "beer" }

        def codeRegistry = newCodeRegistry()
                .dataFetcher("Query", "walksIntoABar", df)
                .fieldValidationRules("Query", "walksIntoABar", continueOn(requiresFirstOrLast))
                .build()
        def runtimeWiring = RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistry).build()

        def graphql = TestUtil.graphQL(spec, runtimeWiring).build()
        when:
        def result = graphql.execute(newExecutionInput().
                query('''
                    query {
                        walksIntoABar
                    }
                '''))
        then:
        result.data["walksIntoABar"] == "beer" // continues on because of rule wrapper
        result.errors[0].message == "You must provide either the first or last argument"
        result.errors[0].path == ["walksIntoABar"]
        result.errors[0].errorType == ErrorType.ValidationError
    }

    def "integration test of rules with input type rules"() {

        DataFetcher df = { env -> "beer" }

        def codeRegistry = newCodeRegistry()
                .dataFetcher("Query", "walksIntoABar", df)
                .inputTypeValidationRules("BarInput", barInputIsAllowed)
                .build()
        def runtimeWiring = RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistry).build()

        def graphql = TestUtil.graphQL(spec, runtimeWiring).build()
        when:
        def result = graphql.execute(newExecutionInput().
                query('''
                    query {
                        walksIntoABar(barInput : { age : 14, hasShoes : false} )
                    }
                '''))
        then:
        result.data["walksIntoABar"] == null // set to null via rule
        result.errors[0].message == "You must be over 18 to enter this bar"
        result.errors[0].path == ["walksIntoABar"]
        result.errors[0].errorType == ErrorType.ValidationError
        result.errors[1].message == "You must be wearing shoes"
        result.errors[1].path == ["walksIntoABar"]
        result.errors[1].errorType == ErrorType.ValidationError
    }

}
