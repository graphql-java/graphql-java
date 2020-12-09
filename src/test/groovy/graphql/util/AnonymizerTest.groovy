package graphql.util

import graphql.AssertException
import graphql.TestUtil
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

class AnonymizerTest extends Specification {

    def "simple schema and query"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            bar1: String
            bar2: ID
        }
        """)
        def query = "{foo{bar1 bar2}}"

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1: Object2
}

type Object2 {
  field2: String
  field3: ID
}
"""
        newQuery == "query {field1 {field2 field3}}"
    }

    def "query with fragments"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            bar1: String
            bar2: ID
        }
        """)
        def query = "{...MyFragment foo {... on Foo{bar1 bar2}}} fragment MyFragment on Query{foo {bar1 bar2 }}"

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1: Object2
}

type Object2 {
  field2: String
  field3: ID
}
"""
        newQuery == "query {...Fragment1 field1 {... on Object2 {field2 field3}}} fragment Fragment1 on Object1 {field1 {field2 field3}}"

    }

    def "query with arguments"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo(id: ID, otherArg: String): Foo
        }
        type Foo {
            bar1(someArg: Boolean): String
            bar2: ID
        }
        """)
        def query = '{foo(id: "123", otherArg: "456") {bar1 bar2 }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1(argument1: ID, argument2: String): Object2
}

type Object2 {
  field2(argument3: Boolean): String
  field3: ID
}
"""
        newQuery == 'query {field1(argument1:"123",argument2:"456") {field2 field3}}'

    }

    def "query with operation and variable names"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo(id: ID, otherArg: String): Foo
        }
        type Foo {
            bar1(someArg: Boolean): String
            bar2(otherId: ID): ID
        }
        """)
        def query = 'query myOperation($myVar:ID) {foo(id: $myVar, otherArg: "456") {bar1 bar2(otherId: $myVar) }}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query], [myVar: "myVarValue"])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        newSchema == """schema {
  query: Object1
}

type Object1 {
  field1(argument1: ID, argument2: String): Object2
}

type Object2 {
  field2(argument3: Boolean): String
  field3(argument4: ID): ID
}
"""
        newQuery == 'query operation($var1:ID) {field1(argument1:$var1,argument2:"456") {field2 field3(argument4:$var1)}}'


    }

    def "rejects query with multiple operations"() {
        given:
        def schema = TestUtil.schema("""
        type Query {
            foo: String
        }
        """)
        def query = 'query myOperation{foo} query myOtherQuery{foo}'

        when:
        def result = Anonymizer.anonymizeSchemaAndQueries(schema, [query])
        def newSchema = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectiveDefinitions(false)).print(result.schema)
        def newQuery = result.queries[0]

        then:
        def assertException = thrown(AssertException)
        assertException.getMessage().contains("Query must have exactly one operation")

    }
}
