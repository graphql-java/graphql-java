package graphql.execution

import graphql.Directives
import graphql.ExecutionInput
import graphql.TestUtil
import graphql.schema.DataFetcher
import spock.lang.Specification

class SemanticNonNullTest extends Specification {

    void setup() {
        Directives.setSemanticNonNullEnabled(true)
    }

    void cleanup() {
        Directives.setSemanticNonNullEnabled(true)
    }

    def "emits an error when a @semanticNonNull scalar resolves to null"() {
        def sdl = '''
            type Query {
                foo : Int @semanticNonNull
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        def ei = ExecutionInput.newExecutionInput("{ foo }").root([foo: null]).build()
        def er = graphql.execute(ei)

        then:
        er.data.foo == null
        er.errors.size() == 1
        er.errors[0].path.toList() == ["foo"]
        er.errors[0].message.contains("semantically non null")
    }

    def "no error when a @semanticNonNull field resolves to a non null value"() {
        def sdl = '''
            type Query {
                foo : Int @semanticNonNull
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        def ei = ExecutionInput.newExecutionInput("{ foo }").root([foo: 42]).build()
        def er = graphql.execute(ei)

        then:
        er.data.foo == 42
        er.errors.isEmpty()
    }

    def "does not emit a second error when one already exists for that path"() {
        def sdl = '''
            type Query {
                foo : Int @semanticNonNull
            }
        '''
        DataFetcher df = { env -> throw new RuntimeException("boom") }
        def graphql = TestUtil.graphQL(sdl, [Query: [foo: df]]).build()

        when:
        def er = graphql.execute("{ foo }")

        then:
        er.data.foo == null
        er.errors.size() == 1
        er.errors[0].path.toList() == ["foo"]
        er.errors[0].message.contains("boom")
    }

    def "the value is not propagated to the parent like a real non null would"() {
        def sdl = '''
            type Query {
                bar : Bar
            }
            type Bar {
                foo : Int @semanticNonNull
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        def ei = ExecutionInput.newExecutionInput("{ bar { foo } }").root([bar: [foo: null]]).build()
        def er = graphql.execute(ei)

        then:
        er.data.bar != null
        er.data.bar.foo == null
        er.errors.size() == 1
        er.errors[0].path.toList() == ["bar", "foo"]
    }

    def "honours the levels argument for list elements"() {
        def sdl = '''
            type Query {
                foo : [Int] @semanticNonNull(levels: [1])
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        def ei = ExecutionInput.newExecutionInput("{ foo }").root([foo: [1, null, 3]]).build()
        def er = graphql.execute(ei)

        then:
        er.data.foo == [1, null, 3]
        er.errors.size() == 1
        er.errors[0].path.toList() == ["foo", 1]
    }

    def "does not synthesize an error for a null list element when only the list itself is semantically non null"() {
        def sdl = '''
            type Query {
                foo : [Int] @semanticNonNull
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        def ei = ExecutionInput.newExecutionInput("{ foo }").root([foo: [1, null, 3]]).build()
        def er = graphql.execute(ei)

        then:
        er.data.foo == [1, null, 3]
        er.errors.isEmpty()
    }

    def "emits an error when a @semanticNonNull list itself is null at level 0"() {
        def sdl = '''
            type Query {
                foo : [Int] @semanticNonNull
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        def ei = ExecutionInput.newExecutionInput("{ foo }").root([foo: null]).build()
        def er = graphql.execute(ei)

        then:
        er.data.foo == null
        er.errors.size() == 1
        er.errors[0].path.toList() == ["foo"]
    }

    def "does nothing when the JVM wide flag is disabled"() {
        def sdl = '''
            type Query {
                foo : Int @semanticNonNull
            }
        '''
        def graphql = TestUtil.graphQL(sdl).build()

        when:
        Directives.setSemanticNonNullEnabled(false)
        def ei = ExecutionInput.newExecutionInput("{ foo }").root([foo: null]).build()
        def er = graphql.execute(ei)

        then:
        er.data.foo == null
        er.errors.isEmpty()
    }

    def "@semanticNonNull does not need to be declared in the SDL"() {
        def sdl = '''
            type Query {
                foo : Int @semanticNonNull
            }
        '''

        when:
        def graphql = TestUtil.graphQL(sdl).build()

        then:
        graphql.getGraphQLSchema().getDirective(Directives.SemanticNonNullDirective.getName()) != null
    }
}
