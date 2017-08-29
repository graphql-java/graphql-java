package graphql.execution

import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLNonNull.nonNull

class NonNullableFieldValidatorTest extends Specification {

    ExecutionContext context = Mock(ExecutionContext)

    def "non nullable field throws exception"() {
        ExecutionTypeInfo typeInfo = ExecutionTypeInfo.newTypeInfo().type(nonNull(GraphQLString)).build()

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context, typeInfo)

        when:
        validator.validate(ExecutionPath.rootPath(), null)

        then:
        thrown(NonNullableFieldWasNullException)

    }

    def "nullable field does not throw exception"() {
        ExecutionTypeInfo typeInfo = ExecutionTypeInfo.newTypeInfo().type(GraphQLString).build()

        NonNullableFieldValidator validator = new NonNullableFieldValidator(context, typeInfo)

        when:
        def result = validator.validate(ExecutionPath.rootPath(), null)

        then:
        result == null
    }
}
