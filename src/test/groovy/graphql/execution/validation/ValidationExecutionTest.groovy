package graphql.execution.validation

import graphql.ErrorType
import graphql.TestUtil
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionPath
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentBuilder
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo

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
                result.withResult(Validations.nonNull(barInput['age'], env, { env2 -> "You must be over 18 to enter this bar" }))
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


    private DataFetchingEnvironment buildDFE(List<ValidationRule> fieldRules, List<ValidationRule> typeRules, Map<String, Object> args) {
        def schema = TestUtil.schema(spec)
        def parentType = schema.getObjectType("Query")
        def fieldDefinition = parentType.getFieldDefinition("walksIntoABar")
        def field = new Field("walksIntoABar")
        def path = ExecutionPath.parse("/walksIntoABar")

        def inputType = schema.getType("BarInput") as GraphQLInputType

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
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


    def "integration test of rules running"() {

        def df = { env -> "beer" }

        def typeWiring = TypeRuntimeWiring.newTypeWiring("Query")
                .dataFetcher("walksIntoABar", df)
                .fieldValidationRules("walksIntoABar", requiresFirstOrLast)
                .build()
        def runtimeWiring = RuntimeWiring.newRuntimeWiring().type(typeWiring).build()

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
}
