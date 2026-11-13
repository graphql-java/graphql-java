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

    def "simple cycle with interfaces"() {
        given:
        def sdl = '''

        type Query {
          hello: [Foo]
        }
        interface Foo {
            foo: Foo 
        }
        type Impl implements Foo {
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

    def "input field cycle"() {
        given:
        def sdl = '''
        type Query {
          hello(i: I): String
        }
        input I {
            foo: I  
        }
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 1
        cycles[0].toString() == "[I.foo, I]"

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

    def "cycle test"() {
        given:
        def sdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
            f1: Foo
            f2: Foo
        }
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 2
        cycles[0].toString() == "[Foo.f1, Foo]"
        cycles[1].toString() == "[Foo.f2, Foo]"


    }

    def "cycle test 2"() {
        given:
        def sdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
            f1: Foo
            f2: Bar
        }
        type Bar {
            foo: Foo
        }
        '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 2
        cycles[0].toString() == "[Foo.f1, Foo]"
        cycles[1].toString() == "[Foo.f2, Bar, Bar.foo, Foo]"

    }

    def "cycle test 3"() {
        given:
        def sdl = '''
       type Query {
            foo: Foo
       } 
       type Foo {
        issues: [IssueConnection]
       }
       type IssueConnection {
       edges: [Edge]
       nodes: [Issue]
       }
       type Edge {
         node: Issue
       }
       type Issue {
           foo: Foo
       } 
       '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        //TODO: should be 2
        cycles.size() == 2
        cycles[0].toString() == "[Foo.issues, IssueConnection, IssueConnection.nodes, Issue, Issue.foo, Foo]"
        cycles[1].toString() == "[Foo.issues, IssueConnection, IssueConnection.edges, Edge, Edge.node, Issue, Issue.foo, Foo]"

    }

    def "cycle test 4"() {
        given:
        def sdl = '''
       type Query {
            foo: Foo
       } 
       type Foo {
        issues: [IssueConnection]
       }
       type IssueConnection {
         edges: [Edge]
           nodes: [Foo]
       }
       type Edge {
         node: Foo
       }
       '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 2
        cycles[0].toString() == "[Foo.issues, IssueConnection, IssueConnection.nodes, Foo]"
        cycles[1].toString() == "[Foo.issues, IssueConnection, IssueConnection.edges, Edge, Edge.node, Foo]"

    }

    def "cycle with Union"() {
        given:
        def sdl = '''
       type Query {
            foo: Foo
       } 
       union Foo = Bar | Baz
       type Bar {
            bar: Foo
       }
       type Baz {
            bar: Foo
       }
       '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema)

        then:
        cycles.size() == 2
        cycles[0].toString() == "[Foo, Baz, Baz.bar]"
        cycles[1].toString() == "[Foo, Bar, Bar.bar]"

    }

    def "introspection cycles "() {
        given:
        def sdl = '''
       type Query {
            hello: String
       } 
       '''
        def schema = TestUtil.schema(sdl)
        when:
        def cycles = CyclicSchemaAnalyzer.findCycles(schema, false)

        then:
        cycles.size() == 6
        cycles[0].toString() == "[__Type.fields, __Field, __Field.type, __Type]"
        cycles[1].toString() == "[__Type.fields, __Field, __Field.args, __InputValue, __InputValue.type, __Type]"
        cycles[2].toString() == "[__Type.interfaces, __Type]"
        cycles[3].toString() == "[__Type.possibleTypes, __Type]"
        cycles[4].toString() == "[__Type.inputFields, __InputValue, __InputValue.type, __Type]"
        cycles[5].toString() == "[__Type.ofType, __Type]"

    }
}
