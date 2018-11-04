package graphql.execution.instrumentation

import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.TestUtil
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.batched.BatchedExecutionStrategy
import graphql.execution.instrumentation.tracing.TracingInstrumentation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import static graphql.execution.instrumentation.tracing.TracingInstrumentation.Options.newOptions

class TracingInstrumentationTest extends Specification {

    def query = """
        {
            hero {
                id
                appearsIn
            }
        }
        """


    def 'tracing captures timings as expected'() {
        given:


        when:

        def instrumentation = new TracingInstrumentation()

        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(testExecutionStrategy)
                .instrumentation(instrumentation)
                .build()

        def executionResult = graphQL.execute(query)
        def extensions = executionResult.getExtensions()
        def specExtensions = executionResult.toSpecification().get("extensions")

        then:

        extensions != null
        specExtensions == extensions

        def tracing = extensions['tracing']

        tracing["version"] == 1L
        tracing["startTime"] != null
        tracing["endTime"] != null
        tracing["duration"] > 0L

        def parsing = tracing['parsing']
        parsing['startOffset'] > 0L
        parsing['duration'] > 0L

        def validation = tracing['validation']
        parsing['startOffset'] > 0L
        parsing['duration'] > 0L

        List resolvers = tracing['execution']['resolvers'] as List
        resolvers.size() == 3
        resolvers[0]['fieldName'] == "hero"
        resolvers[0]['path'] == ["hero"]
        resolvers[0]['startOffset'] > 0L
        resolvers[0]['duration'] > 0L
        resolvers[0]['parentType'] == "QueryType"
        resolvers[0]['returnType'] == "Character"

        resolvers[1]['fieldName'] == "id"
        resolvers[1]['path'] == ["hero", "id"]
        resolvers[1]['startOffset'] > 0L
        resolvers[1]['duration'] > 0L
        resolvers[1]['parentType'] == "Droid"
        resolvers[1]['returnType'] == "String!"

        resolvers[2]['fieldName'] == "appearsIn"
        resolvers[2]['path'] == ["hero", "appearsIn"]
        resolvers[2]['startOffset'] > 0L
        resolvers[2]['duration'] > 0L
        resolvers[2]['parentType'] == "Droid"
        resolvers[2]['returnType'] == "[Episode]"

        // time should add up positively
        long total = tracing["duration"] as long
        long fieldTotals = 0
        resolvers.each { fieldTotals += it['duration'] }
        long partTotals = parsing["duration"] + validation["duration"] + fieldTotals

        total >= partTotals

        where:

        testExecutionStrategy              | _
        new AsyncExecutionStrategy()       | _
        new AsyncSerialExecutionStrategy() | _
        new BatchedExecutionStrategy()     | _
    }

    def "trivial data fetchers are ignored"() {
        given:

        def spec = '''
            type Query {
                hero : Hero
            }
            
            type Hero {
                id : ID
                appearsIn : String
            }
        '''

        DataFetcher df = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return [id: "id", appearsIn: "appearsIn"]
            }

            //isTrivialDataFetcher defaults to false
        }

        def instrumentation = new TracingInstrumentation(
                newOptions().includeTrivialDataFetchers(false)) // defaults to true

        def graphQL = TestUtil.graphQL(spec, [Query: [hero: df]])
                .queryExecutionStrategy(testExecutionStrategy)
                .instrumentation(instrumentation)
                .build()
        when:
        def executionResult = graphQL.execute(query)

        def specExtensions = executionResult.toSpecification().get("extensions")
        def tracing = specExtensions['tracing']

        then:

        tracing["version"] == 1L
        tracing["startTime"] != null
        tracing["endTime"] != null
        tracing["duration"] > 0L

        List resolvers = tracing['execution']['resolvers'] as List
        resolvers.size() == 1
        resolvers[0]['fieldName'] == "hero"
        resolvers[0]['path'] == ["hero"]
        resolvers[0]['startOffset'] > 0L
        resolvers[0]['duration'] > 0L
        resolvers[0]['parentType'] == "Query"
        resolvers[0]['returnType'] == "Hero"

        where:

        testExecutionStrategy              | _
        new AsyncExecutionStrategy()       | _
        new AsyncSerialExecutionStrategy() | _
        new BatchedExecutionStrategy()     | _

    }

    def "default behavior is that trivial fields ARE recorded"() {
        when:
        def options = newOptions()
        then:
        options.isIncludeTrivialDataFetchers()
    }
}
