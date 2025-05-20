package graphql.util.querygenerator


import graphql.TestUtil
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.validation.Validator
import org.junit.Assert
import spock.lang.Specification

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

        def passed = executeTest(schema, fieldPath, expectedNoOperation)

        then:
        passed

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

        passed = executeTest(
                schema,
                fieldPath,
                "barTestOperation",
                "(filter: \"some filter\")",
                null,
                expectedWithOperation,
                QueryGeneratorOptions.defaultOptions().build()
        )

        then:
        passed
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
        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed
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

        def passed = executeTest(schema, fieldPath, expectedNoOperation)

        then:
        passed
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
        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed
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
        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed
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
        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed
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

        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed

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

        passed = executeTest(
                schema,
                fieldPath,
                expected
        )

        then:
        passed
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

        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed
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
        def expected = """
{
  node(id: "1") {
    ... on Bar {
      id
      barName
    }
    ... on Node {
      id
    }
    ... on Foo {
      id
      fooName
    }
  }
}
"""
        def passed = executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        passed

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
        passed = executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        passed

        when: "passing typeClassifier on field that doesn't return an interface"
        fieldPath = "Query.foo"
        classifierType = "Foo"

        executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "typeClassifier should be used only with interface or union types"

        when: "passing typeClassifier that doesn't implement Node"
        fieldPath = "Query.node"
        classifierType = "BazDoesntImplementNode"

        executeTest(schema, fieldPath, null, "(id: \"1\")", classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        e = thrown(IllegalArgumentException)
        e.message == "BazDoesntImplementNode not found in type Node"
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
        def expected = """
{
  something {
    ... on Bar {
      id
      barName
    }
    ... on Foo {
      id
      fooName
    }
  }
}
"""
        def passed = executeTest(schema, fieldPath, null, null, classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        passed

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
        passed = executeTest(schema, fieldPath, null, null, classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        passed

        when: "passing typeClassifier that is not part of the union"
        fieldPath = "Query.something"
        classifierType = "BazIsNotPartOfUnion"

        executeTest(schema, fieldPath, null, null, classifierType, expected, QueryGeneratorOptions.defaultOptions().build())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "BazIsNotPartOfUnion not found in type Something"
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
                .defaultOptions()
                .maxFieldCount(3)
                .build()

        def passed = executeTest(schema, fieldPath, null, null, null, expected, options)

        then:
        passed
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
                .defaultOptions()
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

        def passed = executeTest(schema, fieldPath, null, null, null, expected, options)

        then:
        passed
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

        def passed = executeTest(schema, fieldPath, expected)

        then:
        passed
    }

    private static boolean executeTest(
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
                QueryGeneratorOptions.defaultOptions().build()
        )
    }

    private static boolean executeTest(
            String schemaDefinition,
            String fieldPath,
            String operationName,
            String arguments,
            String typeClassifier,
            String expected,
            QueryGeneratorOptions options
    ) {
        def schema = TestUtil.schema(schemaDefinition)
        def queryGenerator = new QueryGenerator(schema, options)

        def result = queryGenerator.generateQuery(fieldPath, operationName, arguments, typeClassifier)

        executeQuery(result, schema)

        Assert.assertEquals(expected.trim(), result.trim())

        return true
    }

    private static void executeQuery(String query, GraphQLSchema schema) {
        def document = new Parser().parseDocument(query)

        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)

        if (!errors.isEmpty()) {
            Assert.fail("Validation errors: " + errors.collect { it.getMessage() }.join(", "))
        }

    }
}
