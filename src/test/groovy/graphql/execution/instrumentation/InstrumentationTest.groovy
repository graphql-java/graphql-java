package graphql.execution.instrumentation

import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.SimpleExecutionStrategy
import spock.lang.Specification

class InstrumentationTest extends Specification {



    def 'Instrumentation of simple serial execution'() {
        given:

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        //
        // for testing purposes we must use SimpleExecutionStrategy under the covers to get such
        // serial behaviour.  The Instrumentation of a parallel strategy would be much different
        // and certainly harder to test
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

        def instrumentation = new TestingInstrumentation()

        def strategy = new SimpleExecutionStrategy()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentation)
                .build()

        graphQL.execute(query).data

        then:

        instrumentation.executionList == expected
    }

}
