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

    def "oneOf with scalar fields is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { a: String, b: Int }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "oneOf with enum field is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            enum Color { RED GREEN BLUE }
            input A @oneOf { a: Color }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "oneOf with list field is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { a: [A] }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "oneOf referencing non-oneOf input is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { a: RegularInput }
            input RegularInput { x: String }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "oneOf with escape field is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B, escape: String }
            input B @oneOf { a: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "mutually referencing oneOf types with scalar escape is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B }
            input B @oneOf { a: A, escape: Int }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "oneOf referencing non-oneOf with back-reference is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: RegularInput }
            input RegularInput { back: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "multiple fields with chained oneOf escape is inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B, c: C }
            input B @oneOf { a: A }
            input C @oneOf { a: A, escape: String }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "single oneOf self-reference cycle is not inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { self: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].description == "OneOf Input Object A must be inhabited but all fields recursively reference only other OneOf Input Objects forming an unresolvable cycle."
        schemaProblem.errors[0].classification == SchemaValidationErrorType.OneOfNotInhabited
    }

    def "multiple oneOf types forming cycle are not inhabited"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B }
            input B @oneOf { c: C }
            input C @oneOf { a: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 3
        schemaProblem.errors.every { it.classification == SchemaValidationErrorType.OneOfNotInhabited }
    }
}
