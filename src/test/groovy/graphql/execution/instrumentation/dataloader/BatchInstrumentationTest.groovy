package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.TrivialDataFetcher
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class BatchInstrumentationTest extends Specification {

    GraphQL graphQL
    DataLoaderRegistry dataLoaderRegistry
    BatchCompareDataFetchers batchCompareDataFetchers

    void setup() {
        batchCompareDataFetchers = new BatchCompareDataFetchers()
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData(batchCompareDataFetchers)
        dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry()
        Instrumentation instrumentation = new DataLoaderDispatcherInstrumentation()
        graphQL = dataLoaderPerformanceData.setupGraphQL(instrumentation)
    }

    def "batch loading test"() {
        when:
        def rootIssueDf = { env ->
            return env.getDataLoader("issue").load("1");
        } as DataFetcher;

        def insightsIssueDf = { env ->
            return env.getDataLoader("issue").load(env.source["issueId"]);
        } as DataFetcher;

        TrivialDataFetcher insightsDf = env -> {
            return [[issueId: "2"], null, [issueId: "3"], null]
        }
        def Map<String, Map<String, DataFetcher>> dataFetchers = [
                "Query"  : [
                        "issue"   : rootIssueDf,
                        "insights": insightsDf
                ],
                "Insight": [
                        "issue": insightsIssueDf
                ]
        ]

        def schema = TestUtil.schema("""
            type Query {
                issue: Issue
                insights: [Insight]
            }
            
            type Issue {
                id: ID!
                name: String
            }
            type Insight {
                issue: Issue
            }
        """,
                dataFetchers)

        def query = """
            query {
                issue {
                    name
                }
                insights {
                    issue { 
                        name
                    }
                }
            }
        """
        def graphQL = GraphQL.newGraphQL(schema)
                .doNotAddDefaultInstrumentations()
                .instrumentation(new BatchInstrumentation())
                .build();

        BatchLoader<String, List<String>> issueBatchLoader = ids -> {
            println "batch loader with ids: $ids"
            return CompletableFuture.completedFuture([[name: "Issue 1"], [name: "Issue 2"], [name: "Issue 3"]])
        };
        DataLoader<String, List<String>> issueLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("issue", issueLoader)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(dataLoaderRegistry).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
    }

}
