package graphql.execution.instrumentation.idempotency


import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.Execution
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.util.concurrent.CompletableFuture

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLSchema.newSchema

@Stepwise
class IdempotencyTest extends Specification {

    @Shared variableMutation = '''
        mutation MutationTest($key: String, $id: String) {
            mutate(input: {clientMutationId: $key, id: $id}) {
                clientMutationId
                result
            }
        }
    '''

    @Shared inputType = GraphQLInputObjectType.newInputObject()
                    .name("Input")
                    .field(newInputObjectField()
                            .name("clientMutationId")
                            .type(GraphQLString))
                    .field(newInputObjectField()
                            .name("id")
                            .type(GraphQLString))
                    .build()

    @Shared resultType = GraphQLObjectType.newObject()
                    .name("Result")
                    .field(newFieldDefinition()
                            .name("clientMutationId")
                            .type(GraphQLString))
                    .field(newFieldDefinition()
                            .name("result")
                            .type(GraphQLInt))
                    .build()

    @Shared mutationType = GraphQLObjectType.newObject()
                    .name("Mutation")
                    .field(newFieldDefinition()
                            .name("mutate")
                            .type(resultType)
                            .argument(newArgument()
                                    .name("input")
                                    .type(inputType))
                            .dataFetcher(new ResultDataFetcher()))
                    .build()

    @Shared schema = newSchema().query(GraphQLObjectType.newObject().name("Query").build()).mutation(mutationType).build()
    @Shared instrumentation = new IdempotencyInstrumentation()
    @Shared graphql = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()

    def "test repeated relay-compliant variable mutation with identical clientMutationId fails"() {
        given:
        def variables = [
                key: "cc4d57b2-5cd8-43ac-9cb1-c014772101ed",
                id : "265154b0-71ef-455b-b1e6-757b70244006",
        ]

        when:
        setupExecution(variableMutation, variables, 2)

        then:
        def abortExecutionException = thrown(AbortExecutionException)
        def errors = abortExecutionException.getUnderlyingErrors()
        errors.size() == 1
        errors[0].getMessage() == "Mutation with idempotency key " + variables['key'] + " was already processed"
        errors[0].getKey() == variables['key']
        errors[0].getValue().clientMutationId == variables['key']
    }

    def "test repeated relay-compliant non-variable mutation with identical clientMutationId fails"() {
        given:
        def nonVariableIdempotencyKey = "d90d7174-2e25-4cd6-90cb-c259dcef0b24"
        def nonVariableMutation = """
            mutation {
                mutate(input: {clientMutationId: "$nonVariableIdempotencyKey", id: "6e0ab600-9fcd-4ee2-9ce2-d591a0dddf19"}) {
                    clientMutationId
                    result
                }
            }
        """

        when:
        setupExecution(nonVariableMutation, null, 2)

        then:
        def abortExecutionException = thrown(AbortExecutionException)
        def errors = abortExecutionException.getUnderlyingErrors()
        errors.size() == 1
        errors[0].getMessage() == "Mutation with idempotency key " + nonVariableIdempotencyKey + " was already processed"
        errors[0].getKey() == nonVariableIdempotencyKey
        errors[0].getValue().clientMutationId == nonVariableIdempotencyKey
    }

    def "test relay-compliant mutation end-to-end succeeds the first time"() {
        given:
        def variables = [
                key: "0e52b8e0-34bf-4a56-9bb4-768f460a0239",
                id : "39a9b524-3494-439a-b7df-4d61cde2e942",
        ]

        when:
        def executionInput = ExecutionInput.newExecutionInput().query(variableMutation).variables(variables).context("user1").build()
        def result = graphql.execute(executionInput)

        then:
        result.getErrors().size() == 0
        result.getData()["mutate"]["clientMutationId"] == variables["key"]
        result.getData()["mutate"]["result"] == 42
    }

    def "test relay-compliant mutation end-to-end fails the second time with identical idempotency key"() {
        given:
        def variables = [
                key: "0e52b8e0-34bf-4a56-9bb4-768f460a0239",
                id : "5abedc0a-a9e6-4829-96fc-8073d30106b8",
        ]

        when:
        def executionInput = ExecutionInput.newExecutionInput().query(variableMutation).variables(variables).context("user1").build()
        def result = graphql.execute(executionInput)

        then:
        result.errors.size() == 1
        result.errors[0].getMessage() == "Mutation with idempotency key " + variables['key'] + " was already processed"
    }

    private CompletableFuture<ExecutionResult> setupExecution(String query, Map<String, Object> variables, int times) {
        def document = TestUtil.parseQuery(query)
        def strategy = new AsyncExecutionStrategy()
        def instrumentation = new IdempotencyInstrumentation()
        def execution = new Execution(strategy, strategy, strategy, instrumentation)
        def executionInput = ExecutionInput.newExecutionInput().query(query).variables(variables).build()
        for (def i = times; i > 0; i--)
            execution.execute(document, schema, ExecutionId.generate(), executionInput, SimpleInstrumentation.INSTANCE.createState())
    }

    private static class Result {

        String clientMutationId
        Integer result

        Result(String clientMutationId, Integer result) {
            this.clientMutationId = clientMutationId
            this.result = result
        }

    }

    private static class ResultDataFetcher implements DataFetcher<Result> {

        Result get(DataFetchingEnvironment env) throws Exception {
            return new Result(env.getArgument("input")["clientMutationId"], 42)
        }

    }

}
