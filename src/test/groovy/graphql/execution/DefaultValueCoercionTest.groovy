package graphql.execution

import graphql.GraphQLContext
import graphql.TestUtil
import graphql.schema.DataFetcher
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

/**
 * Tests verifying graphql-java's compliance with the default value coercion rules
 * from graphql-spec#793 (merged July 2025).
 *
 * The spec formalizes that default values for arguments and input fields should be
 * coerced according to the same rules applied to user-supplied values. These tests
 * cover:
 *   - Simple argument defaults are coerced
 *   - Input object field defaults are coerced
 *   - Nested defaults (input object with default containing fields that also have defaults)
 *   - Invalid default values are caught at validation time
 *
 * See: https://github.com/graphql/graphql-spec/pull/793
 * Addresses: https://github.com/graphql-java/graphql-java/issues/3983
 */
class DefaultValueCoercionTest extends Specification {

    def "simple scalar argument defaults are coerced at execution time"() {
        given:
        def sdl = '''
            type Query {
                greeting(name: String = "World"): String
                count(n: Int = 42): String
                flag(b: Boolean = true): String
            }
        '''

        def greetingDf = { env -> "Hello, " + env.getArgument("name") } as DataFetcher
        def countDf = { env -> "count=" + env.getArgument("n") } as DataFetcher
        def flagDf = { env -> "flag=" + env.getArgument("b") } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [
                greeting: greetingDf,
                count   : countDf,
                flag    : flagDf
        ]]).build()

        when:
        def ei = newExecutionInput('{ greeting count flag }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [greeting: "Hello, World", count: "count=42", flag: "flag=true"]
    }

    def "enum argument defaults are coerced at execution time"() {
        given:
        def sdl = '''
            enum Color { RED GREEN BLUE }
            type Query {
                paint(color: Color = RED): String
            }
        '''

        def df = { env -> "color=" + env.getArgument("color") } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [paint: df]]).build()

        when:
        def ei = newExecutionInput('{ paint }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [paint: "color=RED"]
    }

    def "input object field defaults are coerced when the field is omitted"() {
        given:
        def sdl = '''
            input PersonInput {
                name: String = "Anonymous"
                age: Int = 0
            }
            type Query {
                greet(person: PersonInput!): String
            }
        '''

        def df = { env ->
            def person = env.getArgument("person") as Map
            "name=${person.name}, age=${person.age}"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [greet: df]]).build()

        when: "only name is provided, age should get its default"
        def ei = newExecutionInput('{ greet(person: {name: "Alice"}) }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [greet: "name=Alice, age=0"]

        when: "only age is provided, name should get its default"
        ei = newExecutionInput('{ greet(person: {age: 30}) }').build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [greet: "name=Anonymous, age=30"]

        when: "neither is provided, both should get their defaults"
        ei = newExecutionInput('{ greet(person: {}) }').build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [greet: "name=Anonymous, age=0"]
    }

    def "nested input object defaults are coerced recursively"() {
        given:
        def sdl = '''
            input AddressInput {
                city: String = "Unknown"
                zip: String = "00000"
            }
            input PersonInput {
                name: String = "Anonymous"
                address: AddressInput = {city: "DefaultCity"}
            }
            type Query {
                info(person: PersonInput!): String
            }
        '''

        def df = { env ->
            def person = env.getArgument("person") as Map
            def addr = person.address as Map
            "name=${person.name}, city=${addr?.city}, zip=${addr?.zip}"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [info: df]]).build()

        when: "no argument fields provided - both person.name and person.address use defaults, address.zip gets its nested default"
        def ei = newExecutionInput('{ info(person: {}) }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        // person.name defaults to "Anonymous"
        // person.address defaults to {city: "DefaultCity"} and address.zip defaults to "00000"
        er.data == [info: "name=Anonymous, city=DefaultCity, zip=00000"]

        when: "person provides an address but omits zip - zip should get its field default"
        ei = newExecutionInput('{ info(person: {name: "Bob", address: {city: "Seattle"}}) }').build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [info: "name=Bob, city=Seattle, zip=00000"]
    }

    def "argument default is an entire input object literal with nested defaults"() {
        given:
        def sdl = '''
            input ConfigInput {
                timeout: Int = 30
                retries: Int = 3
            }
            type Query {
                run(config: ConfigInput = {timeout: 60}): String
            }
        '''

        def df = { env ->
            def config = env.getArgument("config") as Map
            "timeout=${config.timeout}, retries=${config.retries}"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [run: df]]).build()

        when: "no argument provided at all - use the argument default, which itself has a nested field default for retries"
        def ei = newExecutionInput('{ run }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        // argument default is {timeout: 60}, retries not set so uses field default of 3
        er.data == [run: "timeout=60, retries=3"]

        when: "argument provided partially - timeout omitted uses field default"
        ei = newExecutionInput('{ run(config: {retries: 5}) }').build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [run: "timeout=30, retries=5"]
    }

    def "default values for list arguments are coerced"() {
        given:
        def sdl = '''
            type Query {
                sum(numbers: [Int] = [1, 2, 3]): String
            }
        '''

        def df = { env ->
            def numbers = env.getArgument("numbers") as List
            "sum=" + numbers.sum()
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [sum: df]]).build()

        when:
        def ei = newExecutionInput('{ sum }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [sum: "sum=6"]
    }

    def "default values via variables work correctly with coercion"() {
        given:
        def sdl = '''
            input Opts {
                flag: Boolean = false
                label: String = "default"
            }
            type Query {
                check(opts: Opts!): String
            }
        '''

        def df = { env ->
            def opts = env.getArgument("opts") as Map
            "flag=${opts.flag}, label=${opts.label}"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [check: df]]).build()

        when: "variable provides partial input - field defaults fill in"
        def ei = newExecutionInput('query($o: Opts!) { check(opts: $o) }')
                .variables([o: [flag: true]])
                .build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [check: "flag=true, label=default"]

        when: "variable provides empty object - all field defaults apply"
        ei = newExecutionInput('query($o: Opts!) { check(opts: $o) }')
                .variables([o: [:]])
                .build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [check: "flag=false, label=default"]
    }

    def "variable with default value is coerced when variable is not provided"() {
        given:
        def sdl = '''
            type Query {
                hello(name: String): String
            }
        '''

        def df = { env -> "Hello, " + env.getArgument("name") } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [hello: df]]).build()

        when: "variable has a default and is not provided in the variables map"
        def ei = newExecutionInput('query($n: String = "DefaultName") { hello(name: $n) }')
                .variables([:])
                .build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [hello: "Hello, DefaultName"]
    }

    def "invalid default values are caught at schema validation time - programmatic"() {
        when: "building a schema with an invalid programmatic default (string for Int field)"
        TestUtil.schema('''
            type Query {
                field(arg: Int = "notAnInt"): String
            }
        ''')

        then: "schema validation catches the invalid default"
        thrown(Exception)
    }

    def "invalid default values on input object fields are caught at schema validation time"() {
        when:
        TestUtil.schema('''
            input MyInput {
                count: Int = "notAnInt"
            }
            type Query {
                field(arg: MyInput): String
            }
        ''')

        then:
        thrown(Exception)
    }

    def "deeply nested default values coerce correctly through multiple input object levels"() {
        given:
        def sdl = '''
            input InnerInput {
                value: Int = 99
            }
            input MiddleInput {
                inner: InnerInput = {}
                label: String = "mid"
            }
            input OuterInput {
                middle: MiddleInput = {}
            }
            type Query {
                deep(outer: OuterInput = {}): String
            }
        '''

        def df = { env ->
            def outer = env.getArgument("outer") as Map
            def middle = outer.middle as Map
            def inner = middle.inner as Map
            "value=${inner.value}, label=${middle.label}"
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [deep: df]]).build()

        when: "no arguments at all - defaults cascade through all three levels"
        def ei = newExecutionInput('{ deep }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [deep: "value=99, label=mid"]
    }

    def "null default value is coerced as null for nullable fields"() {
        given:
        def sdl = '''
            type Query {
                field(arg: String = null): String
            }
        '''

        def df = { env ->
            def arg = env.getArgument("arg")
            "arg=" + arg
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [field: df]]).build()

        when:
        def ei = newExecutionInput('{ field }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [field: "arg=null"]
    }

    def "default values work correctly when explicit null overrides a non-null default"() {
        given:
        def sdl = '''
            type Query {
                field(arg: String = "hello"): String
            }
        '''

        def df = { env ->
            def arg = env.getArgument("arg")
            "arg=" + arg
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [field: df]]).build()

        when: "explicit null should override the default"
        def ei = newExecutionInput('{ field(arg: null) }').build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [field: "arg=null"]
    }
}
