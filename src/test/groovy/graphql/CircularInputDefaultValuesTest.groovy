package graphql

import graphql.schema.validation.InvalidSchemaException
import spock.lang.Specification

/**
 * Tests for mutually recursive input types with default values.
 *
 * These schemas are now rejected at build time by NoDefaultValueCircularRefs,
 * which detects circular references in input object field default values.
 *
 * Previously, graphql-java accepted these schemas at build time but hit a
 * StackOverflowError at query execution time when the circular defaults were
 * expanded in ValuesResolverConversion.defaultValueToInternalValue.
 */
class CircularInputDefaultValuesTest extends Specification {

    def "mutually recursive input types with default values - rejected at schema build time"() {
        when:
        TestUtil.schema('''
            type Query {
                test(arg: A): String
            }
            input A { b: B = {} }
            input B { a: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference")
    }

    def "self-referential input type with default value - rejected at schema build time"() {
        when:
        TestUtil.schema('''
            type Query {
                test(arg: A): String
            }
            input A { a: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference")
    }

    def "mutually recursive input types with default values - rejected before query execution"() {
        when:
        TestUtil.schema('''
            type Query {
                test(arg: A): String
            }
            input A { b: B = {} }
            input B { a: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference")
    }

    def "self-referential input type with default value - rejected before query execution"() {
        when:
        TestUtil.schema('''
            type Query {
                test(arg: A): String
            }
            input A { a: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference")
    }

    def "mutually recursive defaults via argument default - rejected at schema build time"() {
        when:
        TestUtil.schema('''
            type Query {
                test(arg: A = {}): String
            }
            input A { b: B = {} }
            input B { a: A = {} }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.message.contains("Invalid circular reference")
    }
}
