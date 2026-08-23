package graphql.execution.instrumentation

import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

class InstrumentationStateNullabilityTest extends Specification {

    def "stateless Kotlin instrumentation executes with null state"() {
        given:
        def fixtureClass = Class.forName("graphql.execution.instrumentation.KotlinStatelessInstrumentation")
        def instrumentation = fixtureClass.getDeclaredConstructor().newInstance() as Instrumentation
        def typeRegistry = new SchemaParser().parse("""
            type Query {
              hello: String
            }
        """)
        def schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING)
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()

        when:
        def result = graphQL.execute("{ hello }")

        then:
        result.errors.isEmpty()
        fixtureClass.getMethod("getSeenStates").invoke(instrumentation) == [null]
    }

    def "Kotlin chained instrumentation receives nullable child state"() {
        given:
        def fixtureClass = Class.forName("graphql.execution.instrumentation.KotlinChainedInstrumentation")
        def instrumentation = fixtureClass
                .getDeclaredConstructor(Instrumentation)
                .newInstance(SimplePerformantInstrumentation.INSTANCE) as ChainedInstrumentation
        def state = instrumentation.createStateAsync(null).join()

        when:
        def childState = fixtureClass
                .getMethod("consumeChildState", InstrumentationState)
                .invoke(instrumentation, state)

        then:
        childState == null
    }
}
