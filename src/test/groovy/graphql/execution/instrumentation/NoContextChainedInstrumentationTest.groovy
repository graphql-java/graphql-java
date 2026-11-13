package graphql.execution.instrumentation

import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import spock.lang.Specification

class NoContextChainedInstrumentationTest extends Specification {

    def "basic chaining and state management with no contexts"() {

        def a = new NamedInstrumentation("A")
        def b = new NamedInstrumentation("B")
        def c = new NamedInstrumentation("C")
        def noContextChainedInstrumentation = new NoContextChainedInstrumentation([
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

        // no end: statements becaue the context is never called
        def expected = [
                "start:execution",

                "start:parse",

                "start:validation",

                "start:execute-operation",

                "start:execution-strategy",

                "start:field-hero",
                "start:fetch-hero",
                "start:complete-hero",

                "start:execute-object",

                "start:field-id",
                "start:fetch-id",
                "start:complete-id",
        ]


        when:
        def strategy = new AsyncExecutionStrategy()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(noContextChainedInstrumentation)
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
        assert instrumentation.dfInvocations[0].getExecutionStepInfo().getPath().toList() == ['hero']
        assert instrumentation.dfInvocations[0].getExecutionStepInfo().getUnwrappedNonNullType().name == 'Character'
        assert !instrumentation.dfInvocations[0].getExecutionStepInfo().isNonNullType()

        assert instrumentation.dfInvocations[1].getFieldDefinition().name == 'id'
        assert instrumentation.dfInvocations[1].getExecutionStepInfo().getPath().toList() == ['hero', 'id']
        assert instrumentation.dfInvocations[1].getExecutionStepInfo().getUnwrappedNonNullType().name == 'String'
        assert instrumentation.dfInvocations[1].getExecutionStepInfo().isNonNullType()
    }

}
