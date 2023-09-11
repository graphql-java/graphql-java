package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.validation.ValidationError
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class ChainedInstrumentationStateTest extends Specification {


    def "basic chaining and state management"() {

        def a = new NamedInstrumentation("A")
        def b = new NamedInstrumentation("B")
        def c = new NamedInstrumentation("C")
        def chainedInstrumentation = new ChainedInstrumentation([
                a,
                b,
                c,
        ])

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        def expected = [
                "start:execution",

                "start:parse",
                "end:parse",

                "start:validation",
                "end:validation",

                "start:execute-operation",

                "start:execution-strategy",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",
                "start:complete-hero",

                "start:execute-object",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "start:complete-id",
                "end:complete-id",
                "end:field-id",

                "end:execute-object",

                "end:complete-hero",
                "end:field-hero",

                "end:execution-strategy",

                "end:execute-operation",

                "end:execution",
        ]


        when:
        def strategy = new AsyncExecutionStrategy()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(chainedInstrumentation)
                .build()

        graphQL.execute(query)

        then:

        a.executionList == expected
        b.executionList == expected
        c.executionList == expected

        assertCalls(a)
        assertCalls(b)
        assertCalls(c)

    }

    def "basic chaining and state management when null returned"() {

        def a = new NamedInstrumentation("A")
        def b = new NamedInstrumentation("B") {
            @Override
            InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
                return null // just this method
            }
        }
        def c = new NamedInstrumentation("C")
        def chainedInstrumentation = new ChainedInstrumentation([
                a,
                b,
                c,
        ])

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        def expected = [
                "start:execution",

                "start:parse",
                "end:parse",

                "start:validation",
                "end:validation",

                "start:execute-operation",

                "start:execution-strategy",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",
                "start:complete-hero",

                "start:execute-object",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "start:complete-id",
                "end:complete-id",
                "end:field-id",

                "end:execute-object",

                "end:complete-hero",
                "end:field-hero",

                "end:execution-strategy",

                "end:execute-operation",

                "end:execution",
        ]

        def expectedWhenReturningNull = [
                "start:execution",

                "start:parse",
                "end:parse",

                // overridden above
                //"start:validation",
                //"end:validation",

                "start:execute-operation",

                "start:execution-strategy",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",
                "start:complete-hero",

                "start:execute-object",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "start:complete-id",
                "end:complete-id",
                "end:field-id",

                "end:execute-object",

                "end:complete-hero",
                "end:field-hero",

                "end:execution-strategy",

                "end:execute-operation",

                "end:execution",
        ]


        when:
        def strategy = new AsyncExecutionStrategy()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(chainedInstrumentation)
                .build()

        graphQL.execute(query)

        then:

        a.executionList == expected
        b.executionList == expectedWhenReturningNull
        c.executionList == expected

        assertCalls(a)
        assertCalls(b)
        assertCalls(c)
    }

    def "empty chain"() {
        def chainedInstrumentation = new ChainedInstrumentation(Arrays.asList())

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        when:
        def strategy = new AsyncExecutionStrategy()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(chainedInstrumentation)
                .build()

        graphQL.execute(query)

        then:
        noExceptionThrown()

    }

    def "single chain"() {
        def a = new NamedInstrumentation("A")
        def chainedInstrumentation = new ChainedInstrumentation([a])

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        when:
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(chainedInstrumentation)
                .build()

        graphQL.execute(query)

        then:
        noExceptionThrown()

        assertCalls(a)

    }


    class StringInstrumentationState implements InstrumentationState {
        StringInstrumentationState(String value) {
            this.value = value
        }

        String value
    }

    def "can have an multiple async createState() calls in play"() {


        given:

        def query = '''query Q($var: String!) {
                                  human(id: $var) {
                                    id
                                    name
                                  }
                                }
                            '''


        def instrumentation1 = new SimplePerformantInstrumentation() {
            @Override
            CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
                return CompletableFuture.supplyAsync {
                    return new StringInstrumentationState("I1")
                } as CompletableFuture<InstrumentationState>
            }

            @Override
            CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                return CompletableFuture.completedFuture(
                        executionResult.transform { it.addExtension("i1", ((StringInstrumentationState) state).value) }
                )
            }
        }
        def instrumentation2 = new SimplePerformantInstrumentation() {
            @Override
            CompletableFuture<InstrumentationState> createStateAsync(InstrumentationCreateStateParameters parameters) {
                return CompletableFuture.supplyAsync {
                    return new StringInstrumentationState("I2")
                } as CompletableFuture<InstrumentationState>
            }

            @Override
            CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                return CompletableFuture.completedFuture(
                        executionResult.transform { it.addExtension("i2", ((StringInstrumentationState) state).value) }
                )
            }

        }

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(new ChainedInstrumentation([instrumentation1, instrumentation2]))
                .doNotAddDefaultInstrumentations() // important, otherwise a chained one wil be used
                .build()

        when:
        def variables = [var: "1001"]
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query(query).variables(variables)) // Luke

        then:
        er.extensions == [i1: "I1", i2: "I2"]
    }

    private void assertCalls(NamedInstrumentation instrumentation) {
        assert instrumentation.dfInvocations[0].getFieldDefinition().name == 'hero'
        assert instrumentation.dfInvocations[0].getExecutionStepInfo().getPath().toList() == ['hero']
        assert instrumentation.dfInvocations[0].getExecutionStepInfo().getUnwrappedNonNullType().name == 'Character'
        assert !instrumentation.dfInvocations[0].getExecutionStepInfo().isNonNullType()

        assert instrumentation.dfInvocations[1].getFieldDefinition().name == 'id'
        assert instrumentation.dfInvocations[1].getExecutionStepInfo().getPath().toList() == ['hero', 'id']
        assert instrumentation.dfInvocations[1].getExecutionStepInfo().getUnwrappedNonNullType().name == 'String'
        assert instrumentation.dfInvocations[1].getExecutionStepInfo().isNonNullType()
    }

}
