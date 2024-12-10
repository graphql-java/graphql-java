package graphql.execution

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLNonNull.nonNull

class NonNullableFieldValidatorTest extends Specification {

    ExecutionContext context = Mock(ExecutionContext) {
        propagateErrors() >> true
    }

    def parameters = Mock(ExecutionStrategyParameters) {
        getPath() >> ResultPath.rootPath()
    }

    def "non nullable field throws exception"() {
        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(nonNull(GraphQLString)).build()

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context, typeInfo)

        when:
        validator.validate(parameters, null)

        then:
        thrown(NonNullableFieldWasNullException)

    }

    def "nullable field does not throw exception"() {
        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(GraphQLString).build()

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context, typeInfo)

        when:
        def result = validator.validate(parameters, null)

        then:
        result == null
    }
}
