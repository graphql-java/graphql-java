package graphql

import graphql.schema.DataFetcher
import spock.lang.Specification

class Issue1914 extends Specification {

    def "default values in input objects are respected when variable is not provided"() {
        given:
        def spec = """type Query {
            sayHello(arg: Arg! = {}): String
        }
        input Arg {
            foo: String = "bar"
        }
        """
        DataFetcher df = { dfe ->
            Map arg = dfe.getArgument("arg")
            return arg.get("foo")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def result = graphQL.execute('{sayHello}')

        then:
        result.errors.isEmpty()
        result.data == [sayHello: "bar"]

    }

    def "default values in input objects are overridden when variable is provided"() {
        given:
        def spec = """type Query {
            sayHello(arg: Arg! = {foo: "brewery"}): String
        }
        input Arg {
            foo: String = "bar"
        }
        """
        DataFetcher df = { dfe ->
            Map arg = dfe.getArgument("arg")
            return arg.get("foo")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def result = graphQL.execute('{sayHello}')

        then:
        result.errors.isEmpty()
        result.data == [sayHello: "brewery"]

    }

    def "default values in input objects are overridden when null value is provided in input"() {
        given:
        def spec = """type Query {
            sayHello(arg: Arg! = {foo: null}): String
        }
        input Arg {
            foo: String = "bar"
        }
        """
        DataFetcher df = { dfe ->
            Map arg = dfe.getArgument("arg")
            return arg.get("foo")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def result = graphQL.execute('{sayHello}')

        then:
        result.errors.isEmpty()
        result.data == [sayHello: null]

    }

    def "default values in input objects are overridden when variable is provided and otherwise are respected"() {
        given:
        def spec = """type Query {
            sayHello(arg: Arg! = {field1: "F1ValOverride"}): String
        }
        input Arg {
            field1: String = "F1V"
            field2: String = "F2V"
        }
        """
        DataFetcher df = { dfe ->
            Map arg = dfe.getArgument("arg")
            return arg.get("field1") + " & " + arg.get("field2")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def result = graphQL.execute('{sayHello}')

        then:
        result.errors.isEmpty()
        result.data == [sayHello: "F1ValOverride & F2V"]

    }

    def "default values (including null) in input objects are overridden when variable is provided and otherwise are respected"() {
        given:
        def spec = """type Query {
            sayHello(arg: Arg! = {field1: "F1ValOverride", field5: "F5Val"}): String
        }
        input Arg {
            field1: String = "F1V"
            field2: String = "F2V"
            field3: String = null
            field4: String
            field5: String
        }
        """
        DataFetcher df = { dfe ->
            Map arg = dfe.getArgument("arg")
            return arg.get("field1") + " & " + arg.get("field2") + " & " + arg.get("field3") + " & " + arg.get("field4") + " & " + arg.get("field5")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(spec, ["Query": ["sayHello": df]]).build()

        when:
        def result = graphQL.execute('{sayHello}')

        then:
        result.errors.isEmpty()
        result.data == [sayHello: "F1ValOverride & F2V & null & null & F5Val"]

    }
}

