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

class MaxBatchOperationsInstrumentationTest extends Specification{
    static Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    def "throws exception if number of operations requested exceeds the allowed maximum"() {
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
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} f3: foo { foo {foo {foo {foo{foo{scalar}}}}}} f4: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        MaxBatchOperationsInstrumentation maxBatchOperationsInstrumentation = new MaxBatchOperationsInstrumentation(3)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        when:
        maxBatchOperationsInstrumentation.beginExecuteOperation(executeOperationParameters, null)
        then:
        def e = thrown(AbortExecutionException)
        e.message.contains("maximum request width exceeded 4 > 3")
    }

    def "doesn't throw exception if number of operations are below maximum"() {
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
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} f3: foo {foo {foo {scalar}}} }
            """)
        MaxBatchOperationsInstrumentation maxBatchOperationsInstrumentation = new MaxBatchOperationsInstrumentation(7)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        def state = maxBatchOperationsInstrumentation.createState(null)
        when:
        maxBatchOperationsInstrumentation.beginExecuteOperation(executeOperationParameters, state)
        then:
        notThrown(Exception)
    }

    def "doesn't throw exception if number of operations are below maximum with deprecated beginExecuteOperation"() {
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
        MaxBatchOperationsInstrumentation maxBatchOperationsInstrumentation = new MaxBatchOperationsInstrumentation(7)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        when:
        maxBatchOperationsInstrumentation.beginExecuteOperation(executeOperationParameters, null) // Retain for test coverage
        then:
        notThrown(Exception)
    }

    def "custom max batch operation exceeded function"() {
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
            {f1: foo {foo {foo {scalar}}} f2: foo { foo {foo {foo {foo{foo{scalar}}}}}} f3: foo {scalar} f4: foo {scalar} }
            """)
        Boolean calledFunction = false
        Function<RequestWidthInfo, Boolean> maxBatchOperationsExceededFunction = new Function<RequestWidthInfo, Boolean>() {
            @Override
            Boolean apply(final RequestWidthInfo requestWidthInfo) {
                calledFunction = true
                return false
            }
        }
        MaxBatchOperationsInstrumentation maxBatchOperationsInstrumentation = new MaxBatchOperationsInstrumentation(2, maxBatchOperationsExceededFunction)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        when:
        maxBatchOperationsInstrumentation.beginExecuteOperation(executeOperationParameters, null)
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

        MaxBatchOperationsInstrumentation maxBatchOperationsInstrumentation = new MaxBatchOperationsInstrumentation(6)
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(maxBatchOperationsInstrumentation).build()

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
