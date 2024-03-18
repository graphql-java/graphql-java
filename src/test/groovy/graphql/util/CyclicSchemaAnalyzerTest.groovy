package graphql.util


import graphql.TestUtil
import spock.lang.Specification

class CyclicSchemaAnalyzerTest extends Specification {

    def "simple cycle"() {
        given:
        def sdl = '''

        type Query {
          hello: [Foo]
        }
        type Foo {
            foo: Foo 
        }
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 1
        cycles[0].toString() == "[Foo.foo, Foo]"

    }

    def "multiple cycles"() {
        given:
        def sdl = '''

        type Query {
          hello: [Foo]
        }
        type Foo {
            bar: Bar  
            foo: Foo 
        }
        type Bar {
            bar: [Bar]! 
            foo: Foo
        } 
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 3
        cycles[0].toString() == "[Foo.bar, Bar, Bar.foo, Foo]"
        cycles[1].toString() == "[Foo.foo, Foo]"
        cycles[2].toString() == "[Bar.bar, Bar]"

    }

    def "larger cycle"() {
        given:
        def sdl = '''

        type Query {
          hello: [Foo]
        }
        type Foo {
            bar: Bar 
        }
        type Bar {
            subBar: SubBar 
        }
        type SubBar {
            foo: Foo 
        }
        
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 1
        cycles[0].toString() == "[Foo.bar, Bar, Bar.subBar, SubBar, SubBar.foo, Foo]"

    }

    def "two parents and no cycle"() {
        given:
        def sdl = '''

        type Query {
          hello: Foo1
          hello2: Foo2
        }
        type Foo1 {
            bar: Bar 
        }
        type Foo2 {
            bar: Bar 
        }
        type Bar {
            id: ID
        }
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 0

    }
}
