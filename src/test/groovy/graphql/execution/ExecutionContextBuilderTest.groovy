package graphql.execution

import graphql.GraphQLContext
import graphql.execution.instrumentation.Instrumentation
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

class ExecutionContextBuilderTest extends Specification {

    Instrumentation instrumentation = Mock(Instrumentation)
    ExecutionStrategy queryStrategy = Mock(ExecutionStrategy)
    ExecutionStrategy mutationStrategy = Mock(ExecutionStrategy)
    ExecutionStrategy subscriptionStrategy = Mock(ExecutionStrategy)
    GraphQLSchema schema = Mock(GraphQLSchema)
    def executionId = ExecutionId.generate()
    def context = "context"
    def graphQLContext = GraphQLContext.newContext().build()
    def root = "root"
    Document document = new Parser().parseDocument("query myQuery(\$var: String){...MyFragment} fragment MyFragment on Query{foo}")
    def operation = document.definitions[0] as OperationDefinition
    def fragment = document.definitions[1] as FragmentDefinition
    def dataLoaderRegistry = new DataLoaderRegistry()
    def mockDataLoaderDispatcherStrategy = Mock(DataLoaderDispatchStrategy)

    def "builds the correct ExecutionContext"() {
        when:
        def executionContext = new ExecutionContextBuilder()
                .instrumentation(instrumentation)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .graphQLSchema(schema)
                .executionId(executionId)
                .context(context) // Retain deprecated builder for test coverage
                .graphQLContext(graphQLContext)
                .root(root)
                .operationDefinition(operation)
                .fragmentsByName([MyFragment: fragment])
                .variables([var: 'value']) // Retain deprecated builder for test coverage
                .dataLoaderRegistry(dataLoaderRegistry)
                .dataLoaderDispatcherStrategy(mockDataLoaderDispatcherStrategy)
                .build()

        then:
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.context == context // Retain deprecated method for test coverage
        executionContext.graphQLContext == graphQLContext
        executionContext.getCoercedVariables().toMap() == [var: 'value']
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
        executionContext.dataLoaderRegistry == dataLoaderRegistry
        executionContext.dataLoaderDispatcherStrategy == mockDataLoaderDispatcherStrategy
    }

    def "builds the correct ExecutionContext with coerced variables"() {
        given:
        def coercedVariables = CoercedVariables.of([var: 'value'])

        when:
        def executionContext = new ExecutionContextBuilder()
                .instrumentation(instrumentation)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .graphQLSchema(schema)
                .executionId(executionId)
                .graphQLContext(graphQLContext)
                .root(root)
                .operationDefinition(operation)
                .fragmentsByName([MyFragment: fragment])
                .coercedVariables(coercedVariables)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()

        then:
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.graphQLContext == graphQLContext
        executionContext.coercedVariables == coercedVariables
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
        executionContext.dataLoaderRegistry == dataLoaderRegistry
    }

    def "builds the correct ExecutionContext, if both variables and coercedVariables are set, latest value set takes precedence"() {
        given:
        def coercedVariables = CoercedVariables.of([var: 'value'])

        when:
        def executionContext = new ExecutionContextBuilder()
                .instrumentation(instrumentation)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .graphQLSchema(schema)
                .executionId(executionId)
                .graphQLContext(graphQLContext)
                .root(root)
                .operationDefinition(operation)
                .fragmentsByName([MyFragment: fragment])
                .coercedVariables(coercedVariables)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()

        then:
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.graphQLContext == graphQLContext
        executionContext.coercedVariables == coercedVariables
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
        executionContext.dataLoaderRegistry == dataLoaderRegistry
    }

    def "transform works and copies values with coerced variables"() {
        given:
        def oldCoercedVariables = CoercedVariables.emptyVariables()
        def executionContextOld = new ExecutionContextBuilder()
            .instrumentation(instrumentation)
            .queryStrategy(queryStrategy)
            .mutationStrategy(mutationStrategy)
            .subscriptionStrategy(subscriptionStrategy)
            .graphQLSchema(schema)
            .executionId(executionId)
            .graphQLContext(graphQLContext)
            .root(root)
            .operationDefinition(operation)
            .coercedVariables(oldCoercedVariables)
            .fragmentsByName([MyFragment: fragment])
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()

        when:
        def coercedVariables = CoercedVariables.of([var: 'value'])
        def executionContext = executionContextOld.transform(builder -> builder
                                                        .coercedVariables(coercedVariables))

        then:
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.graphQLContext == graphQLContext
        executionContext.coercedVariables == coercedVariables
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
        executionContext.dataLoaderRegistry == dataLoaderRegistry
    }

    def "transform copies values, if both variables and coercedVariables set, latest value set takes precedence"() {
        given:
        def oldCoercedVariables = CoercedVariables.emptyVariables()
        def executionContextOld = new ExecutionContextBuilder()
                .instrumentation(instrumentation)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .graphQLSchema(schema)
                .executionId(executionId)
                .graphQLContext(graphQLContext)
                .root(root)
                .operationDefinition(operation)
                .coercedVariables(oldCoercedVariables)
                .fragmentsByName([MyFragment: fragment])
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()

        when:
        def coercedVariables = CoercedVariables.of([var: 'value'])
        def executionContext = executionContextOld.transform(builder -> builder
                .coercedVariables(coercedVariables))

        then:
        executionContext.executionId == executionId
        executionContext.instrumentation == instrumentation
        executionContext.graphQLSchema == schema
        executionContext.queryStrategy == queryStrategy
        executionContext.mutationStrategy == mutationStrategy
        executionContext.subscriptionStrategy == subscriptionStrategy
        executionContext.root == root
        executionContext.graphQLContext == graphQLContext
        executionContext.coercedVariables == coercedVariables
        executionContext.getFragmentsByName() == [MyFragment: fragment]
        executionContext.operationDefinition == operation
        executionContext.dataLoaderRegistry == dataLoaderRegistry
    }

    def "transform copies dispatcher"() {
        given:
        def oldCoercedVariables = CoercedVariables.emptyVariables()
        def executionContextOld = new ExecutionContextBuilder()
                .instrumentation(instrumentation)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .graphQLSchema(schema)
                .executionId(executionId)
                .graphQLContext(graphQLContext)
                .root(root)
                .operationDefinition(operation)
                .coercedVariables(oldCoercedVariables)
                .fragmentsByName([MyFragment: fragment])
                .dataLoaderRegistry(dataLoaderRegistry)
                .dataLoaderDispatcherStrategy(DataLoaderDispatchStrategy.NO_OP)
                .build()

        when:
        def executionContext = executionContextOld
                .transform(builder -> builder
                .dataLoaderDispatcherStrategy(mockDataLoaderDispatcherStrategy))

        then:
        executionContext.getDataLoaderDispatcherStrategy() == mockDataLoaderDispatcherStrategy
    }

    def "can detect operation type"() {
        when:
        def executionContext = new ExecutionContextBuilder()
                .instrumentation(instrumentation)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .graphQLSchema(schema)
                .executionId(executionId)
                .graphQLContext(graphQLContext)
                .root(root)
                .operationDefinition(operation)
                .fragmentsByName([MyFragment: fragment])
                .dataLoaderRegistry(dataLoaderRegistry)
                .operationDefinition(OperationDefinition.newOperationDefinition().operation(opType).build())
                .build()

        then:
        executionContext.isQueryOperation() == isQuery
        executionContext.isMutationOperation() == isMutation
        executionContext.isSubscriptionOperation() == isSubscription

        where:
        opType                                     | isQuery | isMutation | isSubscription
        OperationDefinition.Operation.QUERY        | true    | false      | false
        OperationDefinition.Operation.MUTATION     | false   | true       | false
        OperationDefinition.Operation.SUBSCRIPTION | false   | false      | true
    }
}
