package graphql.util.querygenerator


import graphql.TestUtil
import org.junit.Assert
import spock.lang.Specification

class QueryGeneratorTest extends Specification {
    def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        
        type Foo {
            id: ID!
            bar: Bar
            bars: [Bar]
        }
        
        type FooFoo {
            id: ID!
            name: String
            fooFoo: FooFoo
        }
        
        type FooBarFoo {
            id: ID!
            name: String
            barFoo: BarFoo
        }
        
        type BarFoo {
            id: ID!
            name: String
            fooBarFoo: FooBarFoo
        }
        
        type Bar {
           id: ID!
           name: String
           type: TypeEnum
        }
        
        enum TypeEnum {
            FOO
            BAR
        }
        
        """)

    def queryGenerator = new QueryGenerator(
            QueryGenerator.defaultOptions()
                    .schema(schema)
                    .build()
    )

    def printer = new QueryGeneratorPrinter(" ", 2, 0)

    def "generate fields for simple type"() {
        given:

        def typeName = "Bar"

        when:
        def result = queryGenerator.generateQuery(typeName)

        then:
        String printed = printer.print(result)

        Assert.assertEquals(printed.trim(), """
{
  id
  name
  type
}
""".trim())
    }

    def "generate fields for type with nested type"() {
        given:

        def typeName = "Foo"

        when:
        def result = queryGenerator.generateQuery(typeName)

        then:
        String printed = printer.print(result)

        Assert.assertEquals(printed.trim(), """
{
  id
  bar {
    id
    name
    type
  }
  bars {
    id
    name
    type
  }
}
""".trim())
    }

    def "straight forward cyclic dependency"() {
        given:

        def typeName = "FooFoo"

        when:
        def result = queryGenerator.generateQuery(typeName)

        then:
        String printed = printer.print(result)

        Assert.assertEquals(printed.trim(), """

""".trim())
    }

    def "transitive cyclic dependency"() {
        given:

        def typeName = "FooBarFoo"

        when:
        def result = queryGenerator.generateQuery(typeName)

        then:
        String printed = printer.print(result)

        Assert.assertEquals(printed.trim(), """

""".trim())
    }
}
