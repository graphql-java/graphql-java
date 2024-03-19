package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

class OneOfInputObjectRulesTest extends Specification {

    def "oneOf fields must be the right shape"() {

        def sdl = """
            type Query {
                f(arg : OneOfInputType) : String
            }
            
            input OneOfInputType @oneOf {
                ok : String
                badNonNull : String!
                badDefaulted : String = "default"
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 2
        schemaProblem.errors[0].description == "OneOf input field OneOfInputType.badNonNull must be nullable."
        schemaProblem.errors[0].classification == SchemaValidationErrorType.OneOfNonNullableField
        schemaProblem.errors[1].description == "OneOf input field OneOfInputType.badDefaulted cannot have a default value."
        schemaProblem.errors[1].classification == SchemaValidationErrorType.OneOfDefaultValueOnField
    }
}
