package graphql.execution

import graphql.ExecutionResult
import graphql.Scalars
import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.schema.GraphQLList
import graphql.schema.GraphQLObjectType
import spock.lang.Specification

class ExecutionStrategySpec extends Specification {

    ExecutionStrategy executionStrategy

    def setup() {
        executionStrategy = new ExecutionStrategy() {
            @Override
            ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
                return null
            }
        }
    }

    def buildContext() {
        def operationDefinition = new OperationDefinition("query", OperationDefinition.Operation.QUERY, Collections.emptyList(), Collections.emptyList(), new SelectionSet())
        def document = new Document(Collections.singletonList(operationDefinition))
        ExecutionContext.newContext().build(null, executionStrategy, executionStrategy, null, document, "query", null)
    }

    def "completes value for a java.util.List"() {
        given:
        ExecutionContext executionContext = buildContext()
        Field field = new Field()
        def fieldType = new GraphQLList(Scalars.GraphQLString)
        def result = Arrays.asList("test")
        when:
        def executionResult = executionStrategy.completeValue(executionContext, fieldType, [field], result)

        then:
        executionResult.data == ["test"]
    }

    def "completes value for an array"() {
        given:
        ExecutionContext executionContext = buildContext()
        Field field = new Field()
        def fieldType = new GraphQLList(Scalars.GraphQLString)
        String[] result = ["test"]
        when:
        def executionResult = executionStrategy.completeValue(executionContext, fieldType, [field], result)

        then:
        executionResult.data == ["test"]
    }

}
