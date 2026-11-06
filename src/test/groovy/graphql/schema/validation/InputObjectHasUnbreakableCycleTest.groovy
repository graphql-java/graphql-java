package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

/**
 * Tests for the unified InputObjectHasUnbreakableCycle validation rule,
 * which implements the spec's InputObjectHasUnbreakableCycle algorithm.
 *
 * Ported from graphql-js PR #4564 and the existing graphql-java tests.
 *
 * @see <a href="https://github.com/graphql/graphql-spec/pull/1211">graphql-spec PR #1211</a>
 * @see <a href="https://github.com/graphql/graphql-js/pull/4564">graphql-js PR #4564</a>
 */
class InputObjectHasUnbreakableCycleTest extends Specification {

    // ------------------------------------------------------------------
    // Valid schemas: breakable cycles and inhabited types
    // ------------------------------------------------------------------

    def "accepts an Input Object with breakable circular reference"() {
        def sdl = """
            type Query { f(arg: SomeInputObject): String }
            input SomeInputObject {
                self: SomeInputObject
                arrayOfSelf: [SomeInputObject]
                nonNullArrayOfSelf: [SomeInputObject]!
                nonNullArrayOfNonNullSelf: [SomeInputObject!]!
                intermediateSelf: AnotherInputObject
            }
            input AnotherInputObject {
                parent: SomeInputObject
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf Input Object with a scalar field"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { a: Int }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf Input Object with a recursive list field"() {
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

    def "accepts a OneOf Input Object referencing a non-OneOf input object"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B }
            input B { x: Int }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf Input Object referencing an already checked input object"() {
        def sdl = """
            type Query { f(arg: A): String }
            input B { value: Int }
            input A @oneOf { b: B }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf Input Object with multiple acyclic input object fields"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B, c: C }
            input B { value: Int }
            input C { value: Int }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf/OneOf cycle with a scalar escape"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B, escape: Int }
            input B @oneOf { a: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf/non-OneOf cycle with a nullable escape"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B }
            input B { a: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a non-OneOf/non-OneOf cycle with a nullable escape"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A { b: B! }
            input B { a: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a non-OneOf/non-OneOf cycle with a list escape"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A { b: [B!]! }
            input B { a: A! }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts a OneOf/non-OneOf with scalar escape"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B, escape: Int }
            input B { a: A! }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "accepts multiple fields with chained oneOf escape"() {
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

    def "accepts a OneOf with enum field"() {
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

    def "accepts oneOf with scalar fields"() {
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

    def "accepts mutually referencing oneOf types with scalar escape"() {
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

    def "accepts oneOf referencing non-oneOf with back-reference"() {
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

    def "accepts a OneOf Input Object with a non-null recursive list field"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { a: [A!] }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    // ------------------------------------------------------------------
    // Invalid schemas: unbreakable cycles
    // ------------------------------------------------------------------

    def "rejects an Input Object with non-breakable self-reference"() {
        def sdl = """
            type Query { f(arg: SomeInputObject): String }
            input SomeInputObject {
                nonNullSelf: SomeInputObject!
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object SomeInputObject cannot be provided a finite value because it references itself through fields: SomeInputObject.nonNullSelf."
    }

    def "rejects Input Objects with non-breakable circular reference spread across them"() {
        def sdl = """
            type Query { f(arg: SomeInputObject): String }
            input SomeInputObject {
                startLoop: AnotherInputObject!
            }
            input AnotherInputObject {
                nextInLoop: YetAnotherInputObject!
            }
            input YetAnotherInputObject {
                closeLoop: SomeInputObject!
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object AnotherInputObject cannot be provided a finite value because it references itself through fields: AnotherInputObject.nextInLoop, YetAnotherInputObject.closeLoop, SomeInputObject.startLoop."
    }

    def "rejects Input Objects with multiple non-breakable circular references"() {
        def sdl = """
            type Query { f(arg: SomeInputObject): String }
            input SomeInputObject {
                startLoop: AnotherInputObject!
            }
            input AnotherInputObject {
                closeLoop: SomeInputObject!
                startSecondLoop: YetAnotherInputObject!
            }
            input YetAnotherInputObject {
                closeSecondLoop: AnotherInputObject!
                nonNullSelf: YetAnotherInputObject!
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.every {
            it.classification == SchemaValidationErrorType.UnbrokenInputCycle
        }
        schemaProblem.errors.size() == 3
        schemaProblem.errors*.description as Set == [
                "Input Object AnotherInputObject cannot be provided a finite value because it references itself through fields: AnotherInputObject.closeLoop, SomeInputObject.startLoop.",
                "Input Object AnotherInputObject cannot be provided a finite value because it references itself through fields: AnotherInputObject.startSecondLoop, YetAnotherInputObject.closeSecondLoop.",
                "Input Object YetAnotherInputObject cannot be provided a finite value because it references itself through fields: YetAnotherInputObject.nonNullSelf.",
        ] as Set
    }

    def "rejects a self-referencing OneOf type with no escapes"() {
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
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object A cannot be provided a finite value because it references itself through fields: A.self."
    }

    def "rejects a mixed OneOf/non-OneOf cycle with no escapes"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B }
            input B { a: A! }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object A cannot be provided a finite value because it references itself through fields: A.b, B.a."
    }

    def "rejects a larger mixed OneOf/non-OneOf cycle with no escapes"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B }
            input B { c: C! }
            input C @oneOf { a: A }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object A cannot be provided a finite value because it references itself through fields: A.b, B.c, C.a."
    }

    def "rejects multiple oneOf types forming a cycle with no escapes"() {
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
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object A cannot be provided a finite value because it references itself through fields: A.b, B.c, C.a."
    }

    def "reports only the underlying cycle when a regular Input Object requires an unbreakable OneOf"() {
        def sdl = """
            type Query { f(arg: A): String }
            input T @oneOf { self: T }
            input A { t: T! }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object T cannot be provided a finite value because it references itself through fields: T.self."
    }

    def "reports each distinct cycle through multiple OneOf branches"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { b: B, c: C }
            input B { a: A! }
            input C { a: A! }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 2
        schemaProblem.errors.every {
            it.classification == SchemaValidationErrorType.UnbrokenInputCycle
        }
        schemaProblem.errors*.description as Set == [
                "Input Object A cannot be provided a finite value because it references itself through fields: A.b, B.a.",
                "Input Object A cannot be provided a finite value because it references itself through fields: A.c, C.a.",
        ] as Set
    }

    def "ignores required scalar list and finite input fields when reporting a cycle"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A {
                list: [B]!
                finite: Finite!
                b: B!
            }
            input B {
                value: Int!
                a: A!
            }
            input Finite {
                value: Int!
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object A cannot be provided a finite value because it references itself through fields: A.b, B.a."
    }

    def "reports a shared unbreakable OneOf subgraph once"() {
        def chainLength = 20
        def inputTypes = (1..chainLength)
                .collect { index -> "input T${index} @oneOf { a: T${index - 1}, b: T${index - 1} }" }
                .join("\n")
        def sdl = """
            type Query { f(arg: T${chainLength}): String }
            input T0 @oneOf { self: T0 }
            ${inputTypes}
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.UnbrokenInputCycle
        schemaProblem.errors[0].description == "Input Object T0 cannot be provided a finite value because it references itself through fields: T0.self."
    }

    def "does not add an unbreakable cycle error for an empty OneOf"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.InputObjectTypeLackOfFieldError
        schemaProblem.errors[0].description == '"A" must define one or more fields.'
    }

    def "does not add an unbreakable cycle error for a non-null OneOf field"() {
        def sdl = """
            type Query { f(arg: A): String }
            input A @oneOf { self: A! }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.errors.size() == 1
        schemaProblem.errors[0].classification == SchemaValidationErrorType.OneOfNonNullableField
        schemaProblem.errors[0].description == "OneOf input field A.self must be nullable."
    }
}
