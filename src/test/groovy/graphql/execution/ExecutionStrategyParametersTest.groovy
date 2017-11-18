package graphql.execution

import spock.lang.Specification

import static ExecutionTypeInfo.newTypeInfo
import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStrategyParameters.newParameters

class ExecutionStrategyParametersTest extends Specification {

    def "ExecutionParameters can be transformed"() {
        given:
        def parameters = newParameters()
                .typeInfo(newTypeInfo().type(GraphQLString))
                .source(new Object())
                .fields("a": [])
                .build()

        when:
        def newParameters = parameters.transform { it -> it.source(123) }

        then:
        newParameters.typeInfo() == parameters.typeInfo()
        newParameters.fields() == parameters.fields()

        newParameters.source() != parameters.source()
        newParameters.source() == 123
    }

}