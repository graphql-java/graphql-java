package graphql.execution.instrumentation

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.schema.DataFetcher
import graphql.validation.ValidationError
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class ChainedInstrumentationStateTest extends Specification {

    class NamedInstrumentationState implements InstrumentationState {
        String name
    }

    // each implementation gives out a state object with its name
    // and then asserts it gets it back with that name
    class NamedInstrumentation extends TestingInstrumentation {
        String name

        NamedInstrumentation(String name) {
            instrumentationState = new NamedInstrumentationState(name: name)
            this.name = name
        }

        @Override
        InstrumentationState createState() {
            return instrumentationState
        }

        def assertState(InstrumentationState instrumentationState) {
            assert instrumentationState instanceof NamedInstrumentationState
            assert (instrumentationState as NamedInstrumentationState).name == this.name
        }

        @Override
        InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.beginExecution(parameters)
        }

        @Override
        InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.beginParse(parameters)
        }

        @Override
        InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.beginValidation(parameters)
        }

        @Override
        InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.beginDataFetch(parameters)
        }

        @Override
        InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.beginField(parameters)
        }

        @Override
        InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.beginFieldFetch(parameters)
        }

        @Override
        DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.instrumentDataFetcher(dataFetcher, parameters)
        }

        @Override
        CompletableFuture<ExecutionResult> instrumentExecutionResult(CompletableFuture<ExecutionResult> executionResultFuture, InstrumentationExecutionParameters parameters) {
            assertState(parameters.getInstrumentationState())
            return super.instrumentExecutionResult(executionResultFuture, parameters)
        }
    }

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

                "start:data-fetch",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "end:field-id",

                "end:field-hero",

                "end:data-fetch",

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

    private void assertCalls(NamedInstrumentation instrumentation) {
        assert instrumentation.dfInvocations[0].getFieldDefinition().name == 'hero'
        assert instrumentation.dfInvocations[0].getFieldTypeInfo().getPath().toList() == ['hero']
        assert instrumentation.dfInvocations[0].getFieldTypeInfo().getType().name == 'Character'
        assert !instrumentation.dfInvocations[0].getFieldTypeInfo().isNonNullType()

        assert instrumentation.dfInvocations[1].getFieldDefinition().name == 'id'
        assert instrumentation.dfInvocations[1].getFieldTypeInfo().getPath().toList() == ['hero', 'id']
        assert instrumentation.dfInvocations[1].getFieldTypeInfo().getType().name == 'String'
        assert instrumentation.dfInvocations[1].getFieldTypeInfo().isNonNullType()
    }

}
