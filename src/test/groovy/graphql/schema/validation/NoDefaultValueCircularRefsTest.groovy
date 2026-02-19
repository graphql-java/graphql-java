package graphql.schema.validation

import graphql.TestUtil
import spock.lang.Specification

class NoDefaultValueCircularRefsTest extends Specification {
    def "self-referential default value is rejected"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { x: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field A.x references itself.")
    }

    def "mutual recursion through defaults is rejected"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { b: B = {} }
            input B { a: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference")
        e.message.contains("A.b")
    }

    def "transitive cycle through three types is rejected"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: B): String }
            input B { x: B2 = {} }
            input B2 { x: B3 = {} }
            input B3 { x: B = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field B.x references itself via the default values of: B2.x, B3.x.")
    }

    def "self-reference through list wrapping"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: C): String }
            input C { x: [C] = [{}] }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field C.x references itself.")
    }

    def "nested default value that eventually cycles"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: D): String }
            input D { x: D = { x: { x: {} } } }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field D.x references itself.")
    }

    def "cross-field cycle through defaults"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: E): String }
            input E {
                x: E = { x: null }
                y: E = { y: null }
            }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field E.x references itself via the default values of: E.y.")
    }

    def "cycle through non-null wrapping"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: F): String }
            input F { x: F2! = {} }
            input F2 { x: F = { x: {} } }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field F2.x references itself.")
    }

    def "partial default with non-provided recursive field"() {
        when:
        TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { x: B = {name: "hi"} }
            input B {
                name: String
                a: A = {}
            }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference. The default value of Input Object field A.x references itself via the default values of: B.a.")
    }

    def "multiple independent cycles are reported"() {
        when:
        TestUtil.schema('''
            type Query { test(a: A, b: P): String }
            input A { x: A = {} }
            input P { x: P = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("A.x references itself")
        e.message.contains("P.x references itself")
    }

    def "explicit field in default breaks cycle"() {
        when:
        def schema = TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { b: B = {a: null} }
            input B { a: A = {} }
        ''')

        then:
        noExceptionThrown()
        schema.getType("A") != null
    }

    def "recursive field without default does not cycle"() {
        when:
        def schema = TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { b: B = {} }
            input B { a: A }
        ''')

        then:
        noExceptionThrown()
        schema.getType("A") != null
    }

    def "scalar default value does not cycle"() {
        when:
        def schema = TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { name: String = "hi" }
        ''')

        then:
        noExceptionThrown()
        schema.getType("A") != null
    }

    def "null literal default does not cycle"() {
        when:
        def schema = TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { x: A = null }
        ''')

        then:
        noExceptionThrown()
        schema.getType("A") != null
    }

    def "empty list default does not cycle"() {
        when:
        def schema = TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { x: [A] = [] }
        ''')

        then:
        noExceptionThrown()
        schema.getType("A") != null
    }

    def "explicit null on recursive field breaks self-reference"() {
        when:
        def schema = TestUtil.schema('''
            type Query { test(arg: A): String }
            input A { x: A = {x: null} }
        ''')

        then:
        noExceptionThrown()
        schema.getType("A") != null
    }
}
