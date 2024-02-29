package graphql.analysis

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.function.Function

class MaxQueryDepthInstrumentationTest extends Specification {

    static Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    def "throws exception if too deep"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(6)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        when:
        maximumQueryDepthInstrumentation.beginExecuteOperation(executeOperationParameters, null)
        then:
        def e = thrown(AbortExecutionException)
        e.message.contains("maximum query depth exceeded 7 > 6")
    }

    def "doesn't throw exception if not deep enough"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(7)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        def state = null // it has not state in implementation
        when:
        maximumQueryDepthInstrumentation.beginExecuteOperation(executeOperationParameters, state)
        then:
        notThrown(Exception)
    }

    def "doesn't throw exception if not deep enough with deprecated beginExecuteOperation"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(7)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        when:
        maximumQueryDepthInstrumentation.beginExecuteOperation(executeOperationParameters, null) // Retain for test coverage
        then:
        notThrown(Exception)
    }

    def "custom max query depth exceeded function"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                scalar: String  
                foo: Foo
            }
        """)
        def query = createQuery("""
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        Boolean calledFunction = false
        Function<QueryDepthInfo, Boolean> maxQueryDepthExceededFunction = new Function<QueryDepthInfo, Boolean>() {
            @Override
            Boolean apply(final QueryDepthInfo queryDepthInfo) {
                calledFunction = true
                return false
            }
        }
        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(6, maxQueryDepthExceededFunction)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        when:
        maximumQueryDepthInstrumentation.beginExecuteOperation(executeOperationParameters, null)
        then:
        calledFunction
        notThrown(Exception)
    }

    def "coercing null variables that are marked as non nullable wont blow up early"() {

        given:
        def schema = TestUtil.schema("""
            type Query {
                field(arg : String!) : String
            }
        """)

        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(6)
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(maximumQueryDepthInstrumentation).build()

        when:
        def query = '''
            query x($var : String!) {
                field(arg : $var)
            }
        '''
        def executionInput = ExecutionInput.newExecutionInput(query).variables(["var": null]).build()
        def er = graphQL.execute(executionInput)

        then:
        !er.errors.isEmpty()
    }

    static private ExecutionContext executionCtx(ExecutionInput executionInput, Document query, GraphQLSchema schema) {
        ExecutionContextBuilder.newExecutionContextBuilder()
                .executionInput(executionInput).document(query).graphQLSchema(schema).executionId(ExecutionId.generate()).build()
    }
}
