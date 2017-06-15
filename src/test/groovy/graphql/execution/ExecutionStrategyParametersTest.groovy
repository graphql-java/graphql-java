package graphql.execution

import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class ExecutionStrategyParametersTest extends Specification {

    def "ExecutionParameters can be transformed"() {
        given:
        def parameters = new ExecutionStrategyParameters.Builder()
                .typeInfo(new TypeInfo.Builder().type(GraphQLString))
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
