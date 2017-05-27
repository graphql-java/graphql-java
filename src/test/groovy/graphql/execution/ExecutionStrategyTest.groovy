package graphql.execution

import graphql.ExecutionResult
import graphql.GraphQLException
import graphql.Scalars
import graphql.language.Field
import graphql.schema.GraphQLList
import spock.lang.Specification

import static graphql.execution.ExecutionParameters.newParameters

class ExecutionStrategyTest extends Specification {

    ExecutionStrategy executionStrategy

    def setup() {
        executionStrategy = new ExecutionStrategy() {

            @Override
            ExecutionResult execute(ExecutionContext executionContext, ExecutionParameters parameters) {
                return null
            }
        }
    }

    def buildContext() {
        new ExecutionContext(null, null, null, executionStrategy, executionStrategy, executionStrategy, null, null, null, null)
    }


    def "completes value for a java.util.List"() {
        given:
        ExecutionContext executionContext = buildContext()
        Field field = new Field()
        def fieldType = new GraphQLList(Scalars.GraphQLString)
        def result = Arrays.asList("test")
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["fld": []])
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters, [field])

        then:
        executionResult.data == ["test"]
    }

    def "completes value for an array"() {
        given:
        ExecutionContext executionContext = buildContext()
        Field field = new Field()
        def fieldType = new GraphQLList(Scalars.GraphQLString)
        String[] result = ["test"]
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["fld": []])
                .build()

        when:
        def executionResult = executionStrategy.completeValue(executionContext, parameters, [field])

        then:
        executionResult.data == ["test"]
    }

    // this is wrong: we should return null with an error
    def "completing value with serializing throwing exception"() {
        given:
        ExecutionContext executionContext = buildContext()
        def fieldType = Scalars.GraphQLInt
        String result = "not a number"
        def parameters = newParameters()
                .typeInfo(TypeInfo.newTypeInfo().type(fieldType))
                .source(result)
                .fields(["dummy": []])
                .build()

        when:
        executionStrategy.completeValue(executionContext, parameters, [new Field()])

        then:
        thrown(GraphQLException)

    }

}
