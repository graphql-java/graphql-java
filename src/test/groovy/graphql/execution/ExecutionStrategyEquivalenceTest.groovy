package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ForkJoinPool

/**
 * This allows the testing of different execution strategies that provide the same results given the same schema,
 * and queries and results
 */
class ExecutionStrategyEquivalenceTest extends Specification {

    /**
     * @return a simple set of queries and expected results that each execution strategy should
     * return the same result for, even if they use a different strategy
     */
    List<Map<String, Object>> standardQueriesAndResults() {

        def qA = [
                """
        query HeroNameQuery {
          hero {
            name
          }
        }
        """
                : [
                        hero: [
                                name: 'R2-D2'
                        ]
                ]
        ]

        def qB = [
                """
        query HeroNameAndFriendsQuery {
            hero {
                id
                name
                friends {
                    name
                }
            }
        }
        """
                : [
                        hero: [
                                id     : '2001',
                                name   : 'R2-D2',
                                friends: [
                                        [
                                                name: 'Luke Skywalker',
                                        ],
                                        [
                                                name: 'Han Solo',
                                        ],
                                        [
                                                name: 'Leia Organa',
                                        ],
                                ]
                        ]
                ]
        ]

        def qC = [
                """
        query FetchLukeQuery {
            human(id: "1000") {
                name
            }
        }
        """
                : [
                        human: [
                                name: 'Luke Skywalker'
                        ]
                ]
        ]

        def qD = [
                """
        query FetchLukeAndLeiaAliased {
            luke:
            human(id: "1000") {
                name
            }
            leia:
            human(id: "1003") {
                name
            }
        }
        """
                : [
                        luke:
                                [
                                        name: 'Luke Skywalker'
                                ],
                        leia:
                                [
                                        name: 'Leia Organa'
                                ]
                ]
        ]

        [qA, qB, qC, qD]
    }


    @Unroll
    def "execution strategy equivalence (strategy: #strategyType)"() {

        expect:

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategyUnderTest)
                .build()

        for (Map<String, Object> QandR : expectedQueriesAndResults) {
            for (String query : QandR.keySet()) {
                def executionInput = ExecutionInput.newExecutionInput().query(query).build()

                def actualResult = graphQL.execute(executionInput).data

                def expectedResult = QandR[query]
                assert actualResult == expectedResult: "${strategyType} failed with ${query} on expected ${expectedResult}"
            }
        }

        where:

        strategyType      | strategyUnderTest                       | expectedQueriesAndResults
        "async"           | new AsyncExecutionStrategy()            | standardQueriesAndResults()
        "asyncSerial"     | new AsyncSerialExecutionStrategy()      | standardQueriesAndResults()
        "breadthFirst"    | new BreadthFirstExecutionTestStrategy() | standardQueriesAndResults()
        "breadthFirst"    | new BreadthFirstTestStrategy()          | standardQueriesAndResults()

    }

}
