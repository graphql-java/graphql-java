package graphql

import graphql.execution.EngineRunningObserver
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

import static graphql.ExecutionInput.newExecutionInput
import static graphql.execution.EngineRunningObserver.ENGINE_RUNNING_OBSERVER_KEY

class EngineRunningTest extends Specification {


    def "engine running state is observed"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        def df = { env ->
            return "world"
        } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello }"
        def ei = newExecutionInput(query).build()

        List<Boolean> states = new CopyOnWriteArrayList<>();
        ei.getGraphQLContext().put(ENGINE_RUNNING_OBSERVER_KEY, {
            ExecutionId executionId, GraphQLContext context, boolean running ->
                states.add(running)
        } as EngineRunningObserver);

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [hello: "world"]
        states == [true, false]
    }

    def "engine running state is observed with async datafetcher"() {
        given:
        def sdl = '''

        type Query {
          hello: String
        }
        '''
        CompletableFuture cf = new CompletableFuture();
        def df = { env ->
            return cf;
        } as DataFetcher
        def fetchers = ["Query": ["hello": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello }"
        def ei = newExecutionInput(query).build()

        List<Boolean> states = new CopyOnWriteArrayList<>();
        ei.getGraphQLContext().put(ENGINE_RUNNING_OBSERVER_KEY, {
            ExecutionId executionId, GraphQLContext context, boolean running ->
                states.add(running)
        } as EngineRunningObserver);

        when:
        def er = graphQL.executeAsync(ei)
        then:
        states == [true, false]

        when:
        states.clear();
        cf.complete("world")

        then:
        //TODO: why is that so much back and forth between true and false?
        states == [true, false, true, false, true, false, true, false]
        er.get().data == [hello: "world"]

    }


}
