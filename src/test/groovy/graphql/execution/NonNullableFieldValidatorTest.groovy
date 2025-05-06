package graphql.execution

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLNonNull.nonNull

class NonNullableFieldValidatorTest extends Specification {

    def "non nullable field throws exception"() {
        ExecutionContext context = Mock(ExecutionContext) {
            propagateErrorsOnNonNullContractFailure() >> true
        }

        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(nonNull(GraphQLString)).build()

        def parameters = Mock(ExecutionStrategyParameters) {
            getPath() >> ResultPath.rootPath()
            getExecutionStepInfo() >> typeInfo
        }

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context)

        when:
        validator.validate(parameters, null)

        then:
        thrown(NonNullableFieldWasNullException)

    }

    def "nullable field does not throw exception"() {
        ExecutionContext context = Mock(ExecutionContext) {
            propagateErrorsOnNonNullContractFailure() >> true
        }

        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(GraphQLString).build()

        def parameters = Mock(ExecutionStrategyParameters) {
            getPath() >> ResultPath.rootPath()
            getExecutionStepInfo() >> typeInfo
        }

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context)

        when:
        def result = validator.validate(parameters, null)

        then:
        result == null
    }

    def "non nullable field returns null if errors are not propagated"() {
        ExecutionContext context = Mock(ExecutionContext) {
            propagateErrorsOnNonNullContractFailure() >> false
        }

        ExecutionStepInfo typeInfo = ExecutionStepInfo.newExecutionStepInfo().type(nonNull(GraphQLString)).build()

        def parameters = Mock(ExecutionStrategyParameters) {
            getPath() >> ResultPath.rootPath()
            getExecutionStepInfo() >> typeInfo
        }

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context)

        when:
        def result = validator.validate(parameters, null)

        then:
        result == null
    }
}
