package graphql.util.querygenerator


import graphql.TestUtil
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.validation.Validator
import org.junit.Assert
import spock.lang.Specification

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class QueryGeneratorTest extends Specification {
    def "generate query for simple type"() {
        given:
        def schema = """
        type Query {
            bar(filter: String): Bar
        }
        
        type Bar {
           id: ID!
           name: String
           type: TypeEnum
           foos: [String!]!
        }
        
        enum TypeEnum {
            FOO
            BAR
        }
"""

        def fieldPath = "Query.bar"
        when:
        def expectedNoOperation = """
{
  bar {
    ... on Bar {
      id
      name
      type
      foos
    }
  }
}"""

        def result = executeTest(schema, fieldPath, expectedNoOperation)

        then:
        assertNotNull(result)
        assertEquals("Bar", result.usedType)
        assertEquals(4, result.totalFieldCount)
        assertFalse(result.reachedMaxFieldCount)

        when: "operation and arguments are passed"
        def expectedWithOperation = """
query barTestOperation {
  bar(filter: "some filter") {
    ... on Bar {
      id
      name
      type
      foos
    }
  }
}
"""

        result = executeTest(
                schema,
                fieldPath,
                "barTestOperation",
                "(filter: \"some filter\")",
                null,
                expectedWithOperation,
                QueryGeneratorOptions.newBuilder().build()
        )

        then:
        assertNotNull(result)
    }

    def "generate query for type with nested type"() {
        given:
        def schema = """
        type Query {
            foo: Foo
        }
        
        type Foo {
            id: ID!
            bar: Bar
            bars: [Bar]
        }
        
        type Bar {
           id: ID!
           name: String
        }
"""

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      bar {
        id
        name
      }
      bars {
        id
        name
      }
    }
  }
}
"""

        when:
        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "generate query for deeply nested field"() {
        given:
        def schema = """
        type Query {
          bar: Bar
        }
        
        type Bar {
          id: ID!
          foo: Foo
        }
       
       type Foo {
         id: ID!
         baz: Baz
       }
       
       type Baz {
          id: ID!
          name: String   
       } 
        
"""

        def fieldPath = "Query.bar.foo.baz"
        when:
        def expectedNoOperation = """
{
  bar {
    foo {
      baz {
        ... on Baz {
          id
          name
        }
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expectedNoOperation)

        then:
        assertNotNull(result)
    }

    def "straight forward cyclic dependency"() {
        given:
        def schema = """
        type Query {
            fooFoo: FooFoo
        }
        
        type FooFoo {
            id: ID!
            name: String
            fooFoo: FooFoo
        }
"""
        def fieldPath = "Query.fooFoo"
        def expected = """
{
  fooFoo {
    ... on FooFoo {
      id
      name
      fooFoo {
        id
        name
      }
    }
  }
}
"""

        when:
        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "cyclic dependency with 2 fields of the same type"() {
        given:
        def schema = """
        type Query {
            fooFoo: FooFoo
        }
        
        type FooFoo {
            id: ID!
            name: String
            fooFoo: FooFoo
            fooFoo2: FooFoo
        }
"""
        def fieldPath = "Query.fooFoo"
        def expected = """
{
  fooFoo {
    ... on FooFoo {
      id
      name
      fooFoo {
        id
        name
      }
      fooFoo2 {
        id
        name
      }
    }
  }
}
"""

        when:
        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "transitive cyclic dependency"() {
        given:
        def schema = """
        type Query {
            foo: Foo
        }
        
        type Foo {
            id: ID!
            name: String
            bar: Bar
        }
        
        type Bar {
            id: ID!
            name: String
            baz: Baz
        }
        
        type Baz {
            id: ID!
            name: String
            foo: Foo
        }
        
"""
        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      name
      bar {
        id
        name
        baz {
          id
          name
          foo {
            id
            name
          }
        }
      }
    }
  }
}
"""

        when:
        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "generate mutation and subscription for simple type"() {
        given:
        def schema = """
        type Query {
          echo: String
        }
        
        type Mutation {
            bar: Bar
        }
        
        type Subscription {
            bar: Bar
        }
        
        type Bar {
           id: ID!
           name: String
        }
"""


        when: "generate query for mutation"
        def fieldPath = "Mutation.bar"
        def expected = """
mutation {
  bar {
    ... on Bar {
      id
      name
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)

        when: "operation and arguments are passed"

        fieldPath = "Subscription.bar"
        expected = """
subscription {
  bar {
    ... on Bar {
      id
      name
    }
  }
}
"""

        result = executeTest(
                schema,
                fieldPath,
                expected
        )

        then:
        assertNotNull(result)
    }

    def "generate query containing fields with arguments"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
          optionalArg(filter: String): String
          mandatoryArg(id: ID!): String
          mixed(id: ID!, filter: String): String
          defaultArg(filter: String! = "default"): String
          multipleOptionalArgs(filter: String, filter2: String, filter3: String = "default"): String
        }
"""


        when:
        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      optionalArg
      defaultArg
      multipleOptionalArgs
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "generate query for the 'node' field, which returns an interface"() {
        given:
        def schema = """
        type Query {
          node(id: ID!): Node
          foo: Foo
        }

        interface Node {
          id: ID!
        }        
        
        type Foo implements Node {
          id: ID!
          fooName: String
        }
        
        type Bar implements Node {
          id: ID!
          barName: String
        }
        
        type BazDoesntImplementNode {
          id: ID!
          bazName: String
        }
"""


        when:
        def fieldPath = "Query.node"
        def classifierType = null
        def expected = null

        def result = executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "typeName is required for interface types"

        when: "generate query for the 'node' field with a specific type"
        fieldPath = "Query.node"
        classifierType = "Foo"
        expected = """
{
  node(id: "1") {
    ... on Foo {
      id
      fooName
    }
  }
}
"""
        result = executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        assertNotNull(result)

        when: "passing typeName on field that doesn't return an interface"
        fieldPath = "Query.foo"
        classifierType = "Foo"

        executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "typeName should be used only with interface or union types"

        when: "passing typeName that doesn't implement Node"
        fieldPath = "Query.node"
        classifierType = "BazDoesntImplementNode"

        executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Type BazDoesntImplementNode not found in interface Node"
    }

    def "generate query for field which returns an union"() {
        given:
        def schema = """
        type Query {
          something: Something
        }
        
        union Something = Foo | Bar

        type Foo {
          id: ID!
          fooName: String
        }
        
        type Bar {
          id: ID!
          barName: String
        }
        
        type BazIsNotPartOfUnion {
          id: ID!
          bazName: String
        }
"""


        when:
        def fieldPath = "Query.something"
        def classifierType = null
        def expected = null
        def result = executeTest(schema, fieldPath, null, null, classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "typeName is required for union types"

        when: "generate query for field returning union with a specific type"
        fieldPath = "Query.something"
        classifierType = "Foo"
        expected = """
{
  something {
    ... on Foo {
      id
      fooName
    }
  }
}
"""
        result = executeTest(schema, fieldPath, null, null, classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        assertNotNull(result)

        when: "passing typeName that is not part of the union"
        fieldPath = "Query.something"
        classifierType = "BazIsNotPartOfUnion"

        executeTest(schema, fieldPath, null, null, classifierType, expected, QueryGeneratorOptions.newBuilder().build())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Type BazIsNotPartOfUnion not found in union Something"
    }

    def "simple field limit"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
            field1: String
            field2: String
            field3: String
            field4: String
            field5: String
        }
"""


        when:
        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      field1
      field2
      field3
    }
  }
}
"""

        def options = QueryGeneratorOptions
                .newBuilder()
                .maxFieldCount(3)
                .build()

        def result = executeTest(schema, fieldPath, null, null, null, expected, options)

        then:
        assertNotNull(result)
        assertEquals(3, result.totalFieldCount)
        assertTrue(result.reachedMaxFieldCount)
    }

    def "field limit enforcement may result in less fields than the MAX"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
            id: ID!
            bar: Bar
            name: String
            age: Int
        }
        
        type Bar {
            id: ID!
            name: String
        }
"""


        when: "A limit would result on a field container (Foo.bar) having empty field selection"
        def options = QueryGeneratorOptions
                .newBuilder()
                .maxFieldCount(3)
                .build()

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      name
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, null, null, null, expected, options)

        then:
        assertNotNull(result)
    }

    def "max field limit is enforced"() {
        given:
        def queryFieldCount = 20_000
        def queryFields = (1..queryFieldCount).collect { "  field$it: String" }.join("\n")

        def schema = """
    type Query {
      largeType: LargeType
    }

    type LargeType {
$queryFields
    }
"""


        when:

        def fieldPath = "Query.largeType"

        def resultFieldCount = 10_000
        def resultFields = (1..resultFieldCount).collect { "      field$it" }.join("\n")

        def expected = """
{
  largeType {
    ... on LargeType {
$resultFields
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
        assertEquals(10_000, result.totalFieldCount)
        assertTrue(result.reachedMaxFieldCount)
    }

    def "filter types and field"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
            id: ID!
            bar: Bar
            name: String
            age: Int
            baz: Baz
        }
        
        type Bar {
            id: ID!
            name: String
        }
        
        type Baz {
          id: ID!
          name: String
        }
"""


        when:
        def options = QueryGeneratorOptions
                .newBuilder()
                .filterFieldContainerPredicate { it.name != "Bar" }
                .filterFieldDefinitionPredicate { it.name != "name" }
                .build()

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      age
      baz {
        id
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, null, null, null, expected, options)

        then:
        assertNotNull(result)
    }

    def "union fields"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
          id: ID!
          barOrBaz: BarOrBaz
        }
        
        union BarOrBaz = Bar | Baz
        
        type Bar {
          id: ID!
          barName: String
        }
        
        type Baz {
          id: ID!
          bazName: String
        }
"""


        when:

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      barOrBaz {
        ... on Bar {
          Bar_id: id
          Bar_barName: barName
        }
        ... on Baz {
          Baz_id: id
          Baz_bazName: bazName
        }
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "interface fields"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
          id: ID!
          barOrBaz: BarOrBaz
        }
        
        interface BarOrBaz {
          id: ID!
        }
        
        type Bar implements BarOrBaz {
          id: ID!
          barName: String
        }
        
        type Baz implements BarOrBaz {
          id: ID!
          bazName: String
        }
"""


        when:

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      barOrBaz {
        ... on Bar {
          Bar_id: id
          Bar_barName: barName
        }
        ... on Baz {
          Baz_id: id
          Baz_bazName: bazName
        }
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "interface fields with a single implementing type"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
          id: ID!
          alwaysBar: BarInterface
        }
        
        interface BarInterface {
          id: ID!
        }
        
        type Bar implements BarInterface {
          id: ID!
          barName: String
        }
"""


        when:

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      alwaysBar {
        ... on Bar {
          Bar_id: id
          Bar_barName: barName
        }
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "cyclic dependency with union"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
          id: ID!
          bar: Bar
        }
        
        type Bar {
          id: ID!
          baz: Baz
        }
        
        union Baz = Bar | Qux 
        
        type Qux {
          id: ID!
          name: String
        }
        
"""


        when:

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      bar {
        id
        baz {
          ... on Bar {
            Bar_id: id
          }
          ... on Qux {
            Qux_id: id
            Qux_name: name
          }
        }
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "union fields with a single type in union"() {
        given:
        def schema = """
        type Query {
          foo: Foo
        }
        
        type Foo {
          id: ID!
          alwaysBar: BarUnion
        }
        
        union BarUnion = Bar
        
        type Bar {
          id: ID!
          barName: String
        }
"""


        when:

        def fieldPath = "Query.foo"
        def expected = """
{
  foo {
    ... on Foo {
      id
      alwaysBar {
        ... on Bar {
          Bar_id: id
          Bar_barName: barName
        }
      }
    }
  }
}
"""

        def result = executeTest(schema, fieldPath, expected)

        then:
        assertNotNull(result)
    }

    def "generates query for large type"() {
        given:
        def schema = getClass().getClassLoader().getResourceAsStream("extra-large-schema-1.graphqls").text

        when:
        def fieldPath = "Query.node"

        def expected = getClass().getClassLoader().getResourceAsStream("querygenerator/generated-query-for-extra-large-schema-1.graphql").text

        def result = executeTest(schema, fieldPath, null, "(id: \"issue-id-1\")", "JiraIssue", expected, QueryGeneratorOptions.newBuilder().build())

        then:
        assertNotNull(result)
    }

    private static QueryGeneratorResult executeTest(
            String schemaDefinition,
            String fieldPath,
            String expected
    ) {
        return executeTest(
                schemaDefinition,
                fieldPath,
                null,
                null,
                null,
                expected,
                QueryGeneratorOptions.newBuilder().build()
        )
    }

    private static QueryGeneratorResult executeTest(
            String schemaDefinition,
            String fieldPath,
            String operationName,
            String arguments,
            String typeName,
            String expected,
            QueryGeneratorOptions options
    ) {
        def schema = TestUtil.schema(schemaDefinition)
        def queryGenerator = new QueryGenerator(schema, options)

        def result = queryGenerator.generateQuery(fieldPath, operationName, arguments, typeName)
        def query = result.query

        executeQuery(query, schema)

        assertEquals(expected.trim(), query.trim())

        return result
    }

    private static void executeQuery(String query, GraphQLSchema schema) {
        def document = new Parser().parseDocument(query)

        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)

        if (!errors.isEmpty()) {
            Assert.fail("Validation errors: " + errors.collect { it.getMessage() }.join(", "))
        }

    }
}
