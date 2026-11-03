package graphql.execution

import graphql.ExecutionInput
import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.conditional.ConditionalNodeDecision
import graphql.execution.conditional.ConditionalNodeDecisionEnvironment
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

/**
 * The sub fields of a merged field are collected once per object type and reused for every object in the result
 */
class CollectedSubFieldsReuseTest extends Specification {

    def sdl = """
        type Query {
            pets : [Pet]
            dogs : [Dog]
        }
        interface Pet {
            name : String
        }
        type Dog implements Pet {
            name : String
            bark : String
        }
        type Cat implements Pet {
            name : String
            meow : String
        }
    """

    def pets = [
            [type: "Dog", name: "d1", bark: "woof1", meow: "x"],
            [type: "Cat", name: "c1", bark: "x", meow: "meow1"],
            [type: "Dog", name: "d2", bark: "woof2", meow: "x"],
            [type: "Cat", name: "c2", bark: "x", meow: "meow2"],
    ]

    ExecutionContext capturedContext

    GraphQL buildGraphQL() {
        TypeResolver typeResolver = { env -> env.getSchema().getObjectType(((Map) env.getObject()).type as String) }
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("pets", { env -> pets } as DataFetcher)
                        .dataFetcher("dogs", { env -> pets.findAll { it.type == "Dog" } } as DataFetcher))
                .type(newTypeWiring("Pet").typeResolver(typeResolver))
                .build()
        def instrumentation = new Instrumentation() {
            @Override
            ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                capturedContext = executionContext
                return executionContext
            }
        }
        return TestUtil.graphQL(sdl, runtimeWiring).instrumentation(instrumentation).build()
    }

    def "objects of different types in the same list get the fields for their own type"() {
        def query = """
            {
                pets {
                    name
                    ... on Dog { bark }
                    ... on Cat { meow }
                }
            }
        """

        when:
        def er = buildGraphQL().execute(query)

        then:
        er.errors.isEmpty()
        er.data == [pets: [
                [name: "d1", bark: "woof1"],
                [name: "c1", meow: "meow1"],
                [name: "d2", bark: "woof2"],
                [name: "c2", meow: "meow2"],
        ]]
    }

    def "aliased fields with different selections are collected separately"() {
        def query = """
            {
                a: dogs { name }
                b: dogs { bark }
            }
        """

        when:
        def er = buildGraphQL().execute(query)

        then:
        er.errors.isEmpty()
        er.data == [
                a: [[name: "d1"], [name: "d2"]],
                b: [[bark: "woof1"], [bark: "woof2"]],
        ]
    }

    def "fields merged from fragments keep all their sub selections"() {
        def query = """
            {
                dogs { name }
                ... on Query { dogs { bark } }
            }
        """

        when:
        def er = buildGraphQL().execute(query)

        then:
        er.errors.isEmpty()
        er.data == [dogs: [[name: "d1", bark: "woof1"], [name: "d2", bark: "woof2"]]]
    }

    def "sub fields are collected once per field and object type"() {
        def query = """
            {
                pets {
                    name
                    ... on Dog { bark }
                }
            }
        """

        when:
        def er = buildGraphQL().execute(query)

        then:
        er.errors.isEmpty()
        def collected = capturedContext.getCollectedSubFields()
        collected.size() == 1
        def byObjectType = collected.values().first()
        byObjectType.keySet().collect { it.name }.toSet() == ["Dog", "Cat"].toSet()
        byObjectType.find { GraphQLObjectType type, MergedSelectionSet set -> type.name == "Dog" }.value.getKeys() == ["name", "bark"]
        byObjectType.find { GraphQLObjectType type, MergedSelectionSet set -> type.name == "Cat" }.value.getKeys() == ["name"]
    }

    def "a custom conditional node decision is asked for every object"() {
        def query = """
            {
                dogs {
                    name
                    bark
                }
            }
        """
        // this decision is not deterministic, so the collected fields must not be reused
        def calls = 0
        ConditionalNodeDecision decision = new ConditionalNodeDecision() {
            @Override
            boolean shouldInclude(ConditionalNodeDecisionEnvironment env) {
                def directivesContainer = env.getDirectivesContainer()
                if (directivesContainer instanceof Field && directivesContainer.name == "bark") {
                    calls++
                    return calls % 2 == 1
                }
                return true
            }
        }
        def ei = ExecutionInput.newExecutionInput(query)
                .graphQLContext([(ConditionalNodeDecision.class): decision])
                .build()

        when:
        def er = buildGraphQL().execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [dogs: [[name: "d1", bark: "woof1"], [name: "d2"]]]
        calls == 2
        capturedContext.getCollectedSubFields().isEmpty()
    }

    def "collected fields are not reused when incremental support is enabled"() {
        def query = """
            {
                dogs {
                    name
                    bark
                }
            }
        """
        def ei = ExecutionInput.newExecutionInput(query)
                .graphQLContext([(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        when:
        def er = buildGraphQL().execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [dogs: [[name: "d1", bark: "woof1"], [name: "d2", bark: "woof2"]]]
        capturedContext.getCollectedSubFields().isEmpty()
    }
}
