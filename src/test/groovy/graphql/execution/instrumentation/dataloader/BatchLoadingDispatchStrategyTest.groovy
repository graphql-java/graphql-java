package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.TrivialDataFetcher
import graphql.execution.DataLoaderDispatchStrategy
import graphql.execution.ExecutionContext
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Ignore
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.Function

class BatchLoadingDispatchStrategyTest extends Specification {


    def "batch loading with trivial DF"() {
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
                .build();

        int calledCount = 0;
        int idsCount = 0;
        BatchLoader<String, List<String>> issueBatchLoader = ids -> {
            calledCount++;
            idsCount = ids.size();
            println "batch loader with ids: $ids"
            return CompletableFuture.completedFuture([[name: "Issue 1"], [name: "Issue 2"], [name: "Issue 3"]])
        };

        DataLoader<String, List<String>> issueLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("issue", issueLoader)


        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry).build()
        executionInput.getGraphQLContext().put(DataLoaderDispatchStrategy.CUSTOM_STRATEGY_KEY, { executionContext ->
            return new BatchLoadingDispatchStrategy(executionContext)
        } as Function<ExecutionContext, DataLoaderDispatchStrategy>)

        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
        calledCount == 1
        idsCount == 3
    }

    def "test with all fields sync"() {
        given:
//        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]

        def rootIssueDF = { env ->
            return [name: "Issue 1"]
        } as DataFetcher;
        def insightsDF = { env ->
            return [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]
        } as DataFetcher;

        def Map<String, Map<String, DataFetcher>> dataFetchers = [
                "Query": [
                        "issue"   : rootIssueDF,
                        "insights": insightsDF
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
                .build();



        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build()
        executionInput.getGraphQLContext().put(DataLoaderDispatchStrategy.CUSTOM_STRATEGY_KEY, { executionContext ->
            return new BatchLoadingDispatchStrategy(executionContext)
        } as Function<ExecutionContext, DataLoaderDispatchStrategy>)

        when:
        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
    }

    def "default per level batch loading"() {
        when:
        def rootIssueDf = { env ->
            return env.getDataLoader("issue").load("1");
        } as DataFetcher;

        def insightsIssueDf = { env ->
            return env.getDataLoader("issue").load(env.source["issueId"]);
        } as DataFetcher;

        def insightsDf = { env ->
            return [[issueId: "2"], null, [issueId: "3"], null]
        } as DataFetcher

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
                .build();

        int calledCount = 0;
        BatchLoader<String, List<String>> issueBatchLoader = ids -> {
            if (calledCount == 0) {
                calledCount++;
                return CompletableFuture.completedFuture([[name: "Issue 1"]])
            }
            if (calledCount == 1) {
                calledCount++;
                return CompletableFuture.completedFuture([[name: "Issue 2"], [name: "Issue 3"]])
            }
            calledCount++;
            return CompletableFuture.completedFuture([])
        };

        DataLoader<String, List<String>> issueLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("issue", issueLoader)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry).build()
        executionInput.getGraphQLContext().put(DataLoaderDispatchStrategy.CUSTOM_STRATEGY_KEY, { executionContext ->
            return new BatchLoadingDispatchStrategy(executionContext)
        } as Function<ExecutionContext, DataLoaderDispatchStrategy>)

        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
        calledCount == 2
    }

    // failing at the moment .. no dispatch is called
    @Ignore
    def "default per level batch loading 2"() {
        when:

        def friendsPerCharacter = [
                "R2-D2"         : ["Luke Skywalker", "Han Solo", "Leia Organa"],
                "Luke Skywalker": ["Han Solo", "Leia Organa", "C-3P0", "RD-D2"],
                "Han Solo"      : ["Luke Skywalker", "Leia Organa", "R2-D2",],
                "Leia Organa"   : ["Luke Skywalker", "Han Solo", "C-3P0", "RD-D2"],

        ]
        def heroDF = { env ->
            return env.getDataLoader("character").load("R2-D2");
        } as DataFetcher;

        def friendsDF = { env ->
            def friends = friendsPerCharacter[env.source["name"]]
            return env.getDataLoader("character").loadMany(friends);
        } as DataFetcher;

        int calledCount = 0;
        BatchLoader<String, List<String>> characterBatchLoader = names -> {
            calledCount++;
            return CompletableFuture.completedFuture(names.collect { name -> [name: name] })
        };


        Map<String, Map<String, DataFetcher>> dataFetchers = [
                "Query"    : [
                        "hero": heroDF,
                ],
                "Character": [
                        "friends": friendsDF
                ]
        ]
        def schema = TestUtil.schema("""
            type Query {
                hero: Character
            }
            type Character {
                name: String
                friends: [Character]
            }
        """,
                dataFetchers)

        def query = """
            {
              hero {
                name
                friends {
                  name
                  friends {
                    name
                  }
                }
              }
            }
        """


        def graphQL = GraphQL.newGraphQL(schema)
                .build();


        DataLoader<String, List<String>> characterDataLoader = DataLoaderFactory.newDataLoader(characterBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("character", characterDataLoader)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(dataLoaderRegistry).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == [hero: [name: "R2-D2", friends: null]]

        calledCount == 2
    }


//    def "batch loading chained dataloader"() {
//        when:
//        def rootIssueDf = { env ->
//            return ChainedDataLoader.two(env.getDataLoader("issue").load("1"), { result ->
//                return env.getDataLoader("issueDetails").load("1");
//            })
//        } as DataFetcher;
//
//        def insightsIssueDf = { env ->
//            return ChainedDataLoader.two(env.getDataLoader("issue").load(env.source["issueId"]), { result ->
//                return env.getDataLoader("issueDetails").load(env.source["issueId"]);
//            })
//        } as DataFetcher;
//
//        TrivialDataFetcher insightsDf = env -> {
//            return [[issueId: "2"], null, [issueId: "3"], null]
//        }
//        def Map<String, Map<String, DataFetcher>> dataFetchers = [
//                "Query"  : [
//                        "issue"   : rootIssueDf,
//                        "insights": insightsDf
//                ],
//                "Insight": [
//                        "issue": insightsIssueDf
//                ]
//        ]
//
//        def schema = TestUtil.schema("""
//            type Query {
//                issue: Issue
//                insights: [Insight]
//            }
//
//            type Issue {
//                id: ID!
//                name: String
//            }
//            type Insight {
//                issue: Issue
//            }
//        """,
//                dataFetchers)
//
//        def query = """
//            query {
//                issue {
//                    name
//                }
//                insights {
//                    issue {
//                        name
//                    }
//                }
//            }
//        """
//        def graphQL = GraphQL.newGraphQL(schema)
//                .doNotAddDefaultInstrumentations()
//                .instrumentation(new BatchInstrumentation())
//                .build();
//
//        BatchLoader<String, List<String>> issueBatchLoader = ids -> {
//            println "batch loader with ids: $ids"
//            return CompletableFuture.completedFuture([[name: "Issue 1"], [name: "Issue 2"], [name: "Issue 3"]])
//        };
//        BatchLoader<String, List<String>> issueDetailsBatchLoader = ids -> {
//            println "batch loader with ids: $ids"
//            return CompletableFuture.completedFuture([[name: "Issue 1"], [name: "Issue 2"], [name: "Issue 3"]])
//
//            DataLoader<String, List<String>> issueLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);
//            DataLoader<String, List<String>> issueDetailsLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);
//
//            DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
//            dataLoaderRegistry.register("issue", issueLoader)
//
//            ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(dataLoaderRegistry).build()
//            def result = graphQL.execute(executionInput)
//
//            then:
//            result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
//        }
//
//    }


}
