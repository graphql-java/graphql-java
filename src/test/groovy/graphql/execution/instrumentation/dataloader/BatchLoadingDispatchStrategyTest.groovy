package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.TrivialDataFetcher
import graphql.execution.DataLoaderDispatchStrategy
import graphql.execution.ExecutionContext
import graphql.schema.BatchLoaderDataFetcher
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.Function

class BatchLoadingDispatchStrategyTest extends Specification {

    ExecutionInput createExecutionInput(String query, DataLoaderRegistry dataLoaderRegistry) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry).build()
        executionInput.getGraphQLContext().put(DataLoaderDispatchStrategy.CUSTOM_STRATEGY_KEY, { executionContext ->
            return new BatchLoadingDispatchStrategy(executionContext)
        } as Function<ExecutionContext, DataLoaderDispatchStrategy>)

        return executionInput

    }

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


        def executionInput = createExecutionInput(query, dataLoaderRegistry)
        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
        calledCount == 1
        idsCount == 3
    }

    def "batch loading with trivial DF nested"() {
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
        TrivialDataFetcher namespaceDF = env -> {
            return env.source;
        }
        def Map<String, Map<String, DataFetcher>> dataFetchers = [
                "Query"    : [
                        "issue"   : rootIssueDf,
                        "insights": insightsDf
                ],
                "Insight"  : [
                        "namespace": namespaceDF
                ],
                "Namespace": [
                        "issue": insightsIssueDf
                ],
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
                namespace: Namespace
            }
            type Namespace {
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
                    namespace {
                        issue { 
                            name
                        }
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


        def executionInput = createExecutionInput(query, dataLoaderRegistry)
        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[namespace: [issue: [name: "Issue 2"]]], null, [namespace: [issue: [name: "Issue 3"]]], null]]
        calledCount == 1
        idsCount == 3
    }


    def "test with all fields sync"() {
        given:

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



        def executionInput = createExecutionInput(query, EmptyDataLoaderRegistryInstance.EMPTY_DATALOADER_REGISTRY)
        when:
        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
    }

    def "different level same dataloader"() {
        when:
        def rootIssueDf = new BatchLoaderDataFetcher() {
            @Override
            List<String> getDataLoaderNames() {
                return ["issue"]
            }

            @Override
            Object get(DataFetchingEnvironment env) throws Exception {
                return env.getDataLoader("issue").load("1");
            }

        } as BatchLoaderDataFetcher

        def insightsIssueDf = new BatchLoaderDataFetcher() {
            @Override
            List<String> getDataLoaderNames() {
                return ["issue"]
            }

            @Override
            Object get(DataFetchingEnvironment env) throws Exception {
                return env.getDataLoader("issue").load(env.source["issueId"]);
            }
        } as BatchLoaderDataFetcher;

        def insightsDf = { env ->
            return [[issueId: "2"], null, [issueId: "3"], null]
        } as DataFetcher;

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
            calledCount++;
            return CompletableFuture.completedFuture([[name: "Issue 1"], [name: "Issue 2"], [name: "Issue 3"]])
        };

        DataLoader<String, List<String>> issueLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("issue", issueLoader)

        def executionInput = createExecutionInput(query, dataLoaderRegistry)

        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1"], insights: [[issue: [name: "Issue 2"]], null, [issue: [name: "Issue 3"]], null]]
        calledCount == 1
    }

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

        def executionInput = createExecutionInput(query, dataLoaderRegistry)
        def result = graphQL.execute(executionInput)

        then:
        result.data == [hero: [name: "R2-D2", friends: [[name: "Luke Skywalker", friends: [[name: "Han Solo"], [name: "Leia Organa"], [name: "C-3P0"], [name: "RD-D2"]]], [name: "Han Solo", friends: [[name: "Luke Skywalker"], [name: "Leia Organa"], [name: "R2-D2"]]], [name: "Leia Organa", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "C-3P0"], [name: "RD-D2"]]]]]]
        calledCount == 3
    }


    def "batch loading chained dataloader with trivial DF"() {
        given:
        def rootIssueDf = { env ->
            return ChainedDataLoader.two(env.getDataLoader("issue").load("1"), { result ->
                return env.getDataLoader("issueDetails").load(result);
            })
        } as DataFetcher;

        def insightsIssueDf = { env ->
            return ChainedDataLoader.two(env.getDataLoader("issue").load(env.source["issueId"]), { result ->
                return env.getDataLoader("issueDetails").load(result);
            })
        } as DataFetcher;

        TrivialDataFetcher insightsDf = env -> {
            return [[issueId: "2"], null, [issueId: "3"], null]
        }

        def issueNameDF = { env ->
            println "calling issue name batch loader with " + env.source["name"] + " path: " + env.getExecutionStepInfo().getPath()
            return env.getDataLoader("issueName").load(env.source["name"])
        } as DataFetcher;

        def Map<String, Map<String, DataFetcher>> dataFetchers = [
                "Query"  : [
                        "issue"   : rootIssueDf,
                        "insights": insightsDf
                ],
                "Insight": [
                        "issue": insightsIssueDf
                ],
                "Issue"  : [
                        name: issueNameDF
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
                detail: String
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
                    detail
                }
                insights {
                    issue {
                        name
                        detail
                    }
                }
            }
        """
        def graphQL = GraphQL.newGraphQL(schema)
                .build();

        int issueBatchCalledCount = 0;
        BatchLoader<String, List<String>> issueBatchLoader = ids -> {
            println "issue batch loader with ids: $ids"
            issueBatchCalledCount++;
            return CompletableFuture.completedFuture([[name: "Issue 1"], [name: "Issue 2"], [name: "Issue 3"]])
        };
        int issueDetailsBatchCalledCount = 0;
        BatchLoader<String, List<String>> issueDetailsBatchLoader = issues -> {
            println "issue details batch loader with issues: $issues"
            issueDetailsBatchCalledCount++;
            return CompletableFuture.completedFuture([[name: "Issue 1", detail: "Detail 1"], [name: "Issue 2", detail: "Detail 2"], [name: "Issue 3", detail: "Detail 3"]])
        }

        BatchLoader<String, List<String>> issueNameBatchLoader = names -> {
            println "issue name batch loader with names: $names"
            return CompletableFuture.completedFuture(names);
        }

        DataLoader<String, List<String>> issueLoader = DataLoaderFactory.newDataLoader(issueBatchLoader);
        DataLoader<String, List<String>> issueDetailsLoader = DataLoaderFactory.newDataLoader(issueDetailsBatchLoader);
        DataLoader<String, List<String>> issueNameLoader = DataLoaderFactory.newDataLoader(issueNameBatchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("issue", issueLoader)
        dataLoaderRegistry.register("issueDetails", issueDetailsLoader)
        dataLoaderRegistry.register("issueName", issueNameLoader);

        def executionInput = createExecutionInput(query, dataLoaderRegistry)
        when:
        def result = graphQL.execute(executionInput)

        then:
        result.data == [issue: [name: "Issue 1", detail: "Detail 1"], insights: [[issue: [name: "Issue 2", detail: "Detail 2"]], null, [issue: [name: "Issue 3", detail: "Detail 3"]], null]]
        issueBatchCalledCount == 1
        issueDetailsBatchCalledCount == 1
    }


}

