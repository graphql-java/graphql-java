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
    id
    name
    type
    foos
  }
}
"""

        def passed = executeTest(schema, fieldPath, expectedNoOperation)

        then:
        passed

        when: "operation and arguments are passed"
        def expectedWithOperation = """
query barTestOperation {
  bar(filter: "some filter") {
    id
    name
    type
    foos
  }
}
"""

        passed = executeTest(
                schema,
                fieldPath,
                "barTestOperation",
                "(filter: \"some filter\")",
                expectedWithOperation
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
        id
        name
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
    id
    name
    fooFoo {
      id
      name
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
    id
    name
    fooFoo {
      id
      name
      fooFoo2 {
        id
        name
      }
    }
    fooFoo2 {
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
    id
    name
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
    id
    name
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
                expected
        )
    }

    private static boolean executeTest(
            String schemaDefinition,
            String fieldPath,
            String operationName,
            String arguments,
            String expected
    ) {
        def schema = TestUtil.schema(schemaDefinition)
        def queryGenerator = new QueryGenerator(
                QueryGeneratorFieldSelection.defaultOptions()
                        .schema(schema)
                        .build()
        )

        def result = queryGenerator.generateQuery(fieldPath, operationName, arguments)

        executeQuery(result, schema)

        Assert.assertEquals(expected.trim(), result.trim())

        return true
    }

    private static void executeQuery(String query, GraphQLSchema schema) {
        def document = new Parser().parseDocument(query)

        def errors = new Validator().validateDocument(schema, document, Locale.ENGLISH)

        if(!errors.isEmpty()) {
            Assert.fail("Validation errors: " + errors.collect { it.getMessage() }.join(", "))
        }

    }
}
