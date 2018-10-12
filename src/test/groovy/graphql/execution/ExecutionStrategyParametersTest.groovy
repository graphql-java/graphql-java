package graphql.execution

import spock.lang.Specification

import static ExecutionInfo.newExecutionInfo
import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStrategyParameters.newParameters

class ExecutionStrategyParametersTest extends Specification {

    def "ExecutionParameters can be transformed"() {
        given:
        def parameters = newParameters()
                .executionInfo(newExecutionInfo().type(GraphQLString))
                .source(new Object())
                .fields("a": [])
                .build()

        when:
        def newParameters = parameters.transform { it -> it.source(123) }

        then:
        newParameters.getExecutionInfo() == parameters.getExecutionInfo()
        newParameters.getFields() == parameters.getFields()

        newParameters.getSource() != parameters.getSource()
        newParameters.getSource() == 123
    }

}