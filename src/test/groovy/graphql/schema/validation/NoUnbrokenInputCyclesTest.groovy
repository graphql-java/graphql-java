package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

class NoUnbrokenInputCyclesTest extends Specification {

    def "infinitely recursive input type results in error"() {
        when:
        TestUtil.schema('''
            type Query { exists(person: Person): Boolean }
            input Person { friend: Person! }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.errors*.description == ["[friend!] forms an unsatisfiable cycle"]
    }

    def "cycle paths include earlier sibling fields"() {
        when:
        TestUtil.schema('''
            type Query { a(arg: A): String }
            input A { x: B! y: A! }
            input B { a: A! }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.errors*.description as Set == [
                "[x!.a!] forms an unsatisfiable cycle",
                "[x!.y!] forms an unsatisfiable cycle",
        ] as Set
    }

    def "input object cycles through a nullable field are allowed"() {
        when:
        TestUtil.schema('''
            type Query { f(a: A): String }
            input A { b: B }
            input B { a: A! }
        ''')

        then:
        noExceptionThrown()
    }

    def "input object cycles through a non-null list are allowed"() {
        def sdl = """
            input Example {
                self: [Example!]!
                value: String
            }

            type Query {
                example(example: Example): String
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "longer input object cycles through a non-null list are allowed"() {
        def sdl = """
            input Foo {
                bar: Bar!
            }

            input Bar {
                baz: Baz!
            }

            input Baz {
                foos: [Foo!]!
            }

            type Query {
                foo(foo: Foo): String
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }
}
