package graphql.util.querygenerator


import graphql.TestUtil
import org.junit.Assert
import spock.lang.Specification

class QueryGeneratorTest extends Specification {
    def printer = new QueryGeneratorPrinter(" ", 2, 0)

    def "generate fields for simple type"() {
        given:
        def schema = """
        type Query {
            bar: Bar
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

        def typeName = "Bar"
        def expected = """
{
  id
  name
  type
  foos
}
"""

        when:
        def passed = executeTest(schema, typeName, expected)

        then:
        passed
    }

    def "generate fields for type with nested type"() {
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

        def typeName = "Foo"
        def expected = """
{
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
"""

        when:
        def passed = executeTest(schema, typeName,  expected)

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
        def typeName = "FooFoo"
        def expected = """
{
  id
  name
  fooFoo {
    id
    name
  }
}
"""

        when:
        def passed = executeTest(schema, typeName, expected)

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
        def typeName = "FooFoo"
        def expected = """
{
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
"""

        when:
        def passed = executeTest(schema, typeName, expected)

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
        def typeName = "Foo"
        def expected = """
{
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
"""

        when:
        def passed = executeTest(schema, typeName, expected)

        then:
        passed
    }

    private boolean executeTest(
            String schema,
            String typeName,
            String expected
    ) {
        def queryGenerator = new QueryGenerator(
                QueryGenerator.defaultOptions()
                        .schema(TestUtil.schema(schema))
                        .build()
        )

        def result = queryGenerator.generateQuery(typeName)
        String printed = printer.print(result)

        Assert.assertEquals(expected.trim(), printed.trim())

        return true
    }
}
