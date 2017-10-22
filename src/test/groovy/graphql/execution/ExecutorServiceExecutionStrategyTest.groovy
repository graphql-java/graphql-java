package graphql.execution

import graphql.GraphQL
import graphql.StarWarsSchema
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ExecutorServiceExecutionStrategyTest extends Specification {

    def 'Example usage of ExecutorServiceExecutionStrategy.'() {
        given:
        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
                friends {
                    name
                }
            }
        }
        """
        def expected = [
                hero: [
                        id     : '2001',
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

        when:
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>() {
            @Override
            boolean offer(Runnable e) {
                /* queue that always rejects tasks */
                return false
            }
        }

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                2, /* core pool size 2 thread */
                2, /* max pool size 2 thread */
                30, TimeUnit.SECONDS,
                /*
                 * Do not use the queue to prevent threads waiting on enqueued tasks.
                 */
                queue,
                /*
                 *  If all the threads are working, then the caller thread
                 *  should execute the code in its own thread. (serially)
                 */
                new ThreadPoolExecutor.CallerRunsPolicy())

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(new ExecutorServiceExecutionStrategy(threadPoolExecutor))
                .build()
        def result = graphQL.execute(query).data

        then:
        result == expected
    }
}
