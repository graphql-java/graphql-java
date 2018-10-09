package graphql

import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

class ExecutionInputTest extends Specification {

    def query = "query { hello }"
    def registry = new DataLoaderRegistry()
    def root = "root"
    def context = "context"
    def variables = [key: "value"]

    def "build works"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .variables(variables)
                .root(root)
                .context(context)
                .build()
        then:
        executionInput.context == context
        executionInput.root == root
        executionInput.variables == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.query == query
    }

    def "transform works and copies values"() {
        when:
        def executionInputOld = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
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
        executionInput.query == "new query"
    }
}
