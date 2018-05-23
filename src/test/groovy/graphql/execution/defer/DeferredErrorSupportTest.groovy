package graphql.execution.defer

import graphql.Directives
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.reactivestreams.Publisher
import spock.lang.Specification

import static graphql.TestUtil.schema
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring
import static org.awaitility.Awaitility.await

class DeferredErrorSupportTest extends Specification {

    def "#1040 errors in stage one do not affect deferred stages"() {

        def spec = '''
            type Query {
                stage1 : String
                stage2 : String
            }
        '''

        def bangDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                throw new RuntimeException("bang-" + environment.getField().getName())
            }
        }

        def runtimeWiring = newRuntimeWiring().type(
                newTypeWiring("Query")
                        .dataFetchers([
                        stage1: bangDF,
                        stage2: bangDF,
                ])
        ).build()

        def schema = schema(spec, runtimeWiring).transform({ b -> b.additionalDirective(Directives.DeferDirective) })
        def graphql = GraphQL.newGraphQL(schema).build()

        when:
        def executionResult = graphql.execute('''
            {
                stage1,
                stage2 @defer
            }
        ''')

        then:
        executionResult.errors.size() == 1
        executionResult.errors[0].getMessage().contains("bang-stage1")

        when:
        def executionResultDeferred = null
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult executionResultStreamed) {
                executionResultDeferred = executionResultStreamed
                subscription.request(1)
            }
        }
        Publisher<ExecutionResult> deferredResultStream = executionResult.extensions[GraphQL.DEFERRED_RESULTS] as Publisher<ExecutionResult>
        deferredResultStream.subscribe(subscriber)

        await().untilTrue(subscriber.finished)

        then:
        executionResultDeferred.errors.size() == 1
        executionResultDeferred.errors[0].getMessage().contains("bang-stage2")

    }
}
