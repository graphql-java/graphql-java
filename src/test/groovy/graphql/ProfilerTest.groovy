package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

class ProfilerTest extends Specification {


    def "one field"() {
        given:
        def sdl = '''
            type Query {
                hello: String
            }
        '''
        def schema = TestUtil.schema(sdl, [Query: [
                hello: { DataFetchingEnvironment dfe -> return "world" } as DataFetcher
        ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ hello }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [hello: "world"]

        then:
        profilerResult.getFieldsFetched() == ["hello"] as Set

    }

    def "two DF with list"() {
        given:
        def sdl = '''
            type Query {
                foo: [Foo]
            }
            type Foo {
                id: String
                bar: String
            }
        '''
        def schema = TestUtil.schema(sdl, [
                Query: [
                        foo: { DataFetchingEnvironment dfe -> return [[id: "1"], [id: "2"], [id: "3"]] } as DataFetcher],
                Foo  : [
                        bar: { DataFetchingEnvironment dfe -> dfe.source.id } as DataFetcher
                ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ foo { id bar } }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [foo: [[id: "1", bar: "1"], [id: "2", bar: "2"], [id: "3", bar: "3"]]]

        then:
        profilerResult.getFieldsFetched() == ["foo", "foo/bar", "foo/id"] as Set
        profilerResult.getTotalDataFetcherInvocations() == 7
        profilerResult.getTotalCustomDataFetcherInvocations() == 4
        profilerResult.getTotalPropertyDataFetcherInvocations() == 3
    }

    def "records timing"() {
        given:
        def sdl = '''
            type Query {
                foo: Foo
            }
            type Foo {
                id: String
            }
        '''
        def schema = TestUtil.schema(sdl, [
                Query: [
                        foo: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.supplyAsync {
                                Thread.sleep(500)
                                "1"
                            }
                        } as DataFetcher],
                Foo  : [
                        id: { DataFetchingEnvironment dfe ->
                            return CompletableFuture.supplyAsync {
                                Thread.sleep(500)
                                dfe.source
                            }
                        } as DataFetcher
                ]])
        def graphql = GraphQL.newGraphQL(schema).build();

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("{ foo { id  } }")
                .profileExecution(true)
                .build()

        when:
        def result = graphql.execute(ei)
        def profilerResult = ei.getGraphQLContext().get(ProfilerResult.PROFILER_CONTEXT_KEY) as ProfilerResult

        then:
        result.getData() == [foo: [id: "1"]]
        // the total execution time must be more than 1 second,
        // the engine should take less than 500ms
        profilerResult.getTotalExecutionTime() > Duration.ofSeconds(1).toNanos()
        profilerResult.getEngineTotalRunningTime() > Duration.ofMillis(1).toNanos()
        profilerResult.getEngineTotalRunningTime() < Duration.ofMillis(500).toNanos()


    }


}
