package graphql.execution

import graphql.execution.instrumentation.Instrumentation
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class ExecutionContextBuilderTest extends Specification {


    def "builds the correct ExecutionContext"() {
        given:
        ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder()

        Instrumentation instrumentation = Mock(Instrumentation)
        executionContextBuilder.instrumentation(instrumentation)

        ExecutionStrategy queryStrategy = Mock(ExecutionStrategy)
        executionContextBuilder.queryStrategy(queryStrategy)

        ExecutionStrategy mutationStrategy = Mock(ExecutionStrategy)
        executionContextBuilder.mutationStrategy(mutationStrategy)

        ExecutionStrategy subscriptionStrategy = Mock(ExecutionStrategy)
        executionContextBuilder.subscriptionStrategy(subscriptionStrategy)

        GraphQLSchema schema = Mock(GraphQLSchema)
        executionContextBuilder.graphQLSchema(schema)

        def executionId = ExecutionId.generate()
        executionContextBuilder.executionId(executionId)

        def context = "context"
        executionContextBuilder.context(context)

        def root = "root"
        executionContextBuilder.root(root)

        Document document = new Parser().parseDocument("query myQuery(\$var: String){...MyFragment} fragment MyFragment on Query{foo}")
        def operation = document.definitions[0] as OperationDefinition
        def fragment = document.definitions[1] as FragmentDefinition
        executionContextBuilder.operationDefinition(operation)

        executionContextBuilder.fragmentsByName([MyFragment: fragment])

        def variables = Collections.emptyMap()
        executionContextBuilder.variables(variables)

        executionContextBuilder.variables([var: 'value'])

        when:
        def executionContext = executionContextBuilder.build()

        then:
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.context == context
        executionContext.variables == [var: 'value']
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
    }
}
