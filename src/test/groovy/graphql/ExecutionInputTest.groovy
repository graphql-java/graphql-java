package graphql

import graphql.cachecontrol.CacheControl
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.function.UnaryOperator

class ExecutionInputTest extends Specification {

    def query = "query { hello }"
    def registry = new DataLoaderRegistry()
    def cacheControl = CacheControl.newCacheControl()
    def root = "root"
    def context = "context"
    def variables = [key: "value"]

    def "build works"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .cacheControl(cacheControl)
                .variables(variables)
                .root(root)
                .context(context)
                .build()
        then:
        executionInput.context == context
        executionInput.root == root
        executionInput.variables == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.cacheControl == cacheControl
        executionInput.query == query
    }

    def "context methods work"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .context({ builder -> builder.of("k1", "v1") } as UnaryOperator)
                .build()
        then:
        (executionInput.context as GraphQLContext).get("k1") == "v1"

        when:
        executionInput = ExecutionInput.newExecutionInput().query(query)
                .context(GraphQLContext.newContext().of("k2", "v2"))
                .build()
        then:
        (executionInput.context as GraphQLContext).get("k2") == "v2"
    }

    def "context is defaulted"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()
        then:
        executionInput.context instanceof GraphQLContext
    }

    def "transform works and copies values"() {
        when:
        def executionInputOld = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .cacheControl(cacheControl)
                .variables(variables)
                .root(root)
                .context(context)
                .build()
        def executionInput = executionInputOld.transform({ bldg -> bldg.query("new query") })

        then:
        executionInput.context == context
        executionInput.root == root
        executionInput.variables == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.cacheControl == cacheControl
        executionInput.query == "new query"
    }

    def "defaults query into builder as expected"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput("{ q }").build()
        then:
        executionInput.query == "{ q }"
        executionInput.cacheControl != null
        executionInput.dataLoaderRegistry != null
        executionInput.variables == [:]
    }
}
