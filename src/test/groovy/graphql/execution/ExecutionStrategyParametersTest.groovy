package graphql.execution

import spock.lang.Specification

import static ExecutionStepInfo.newExecutionStepInfo
import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStrategyParameters.newParameters

class ExecutionStrategyParametersTest extends Specification {

    def "ExecutionParameters can be transformed"() {
        given:
        def parameters = newParameters()
                .executionStepInfo(newExecutionStepInfo().type(GraphQLString))
                .source(new Object())
                .fields("a": [])
                .build()

        when:
        def newParameters = parameters.transform { it -> it.source(123) }

        then:
        newParameters.getExecutionStepInfo() == parameters.getExecutionStepInfo()
        newParameters.getFields() == parameters.getFields()

        newParameters.getSource() != parameters.getSource()
        newParameters.getSource() == 123
    }

}