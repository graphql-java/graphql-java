package graphql.analysis

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.function.Function

class MaxQueryComplexityInstrumentationTest extends Specification {

    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }


    def "default complexity calculator"() {
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
            {f2: foo {scalar foo{scalar}} f1: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(10)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def state = createInstrumentationState(queryComplexityInstrumentation)
        InstrumentationExecuteOperationParameters executeOperationParameters = createExecuteOperationParameters(queryComplexityInstrumentation, executionInput, query, schema, state)
        when:
        queryComplexityInstrumentation.beginExecuteOperation(executeOperationParameters, state)
        then:
        def e = thrown(AbortExecutionException)
        e.message == "maximum query complexity exceeded 11 > 10"

    }


    def "complexity calculator works with __typename field with score 0"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: String 
            }
        """)
        def query = createQuery("""
            { f1: foo f2: foo __typename }
            """)
        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(1)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def state = createInstrumentationState(queryComplexityInstrumentation)
        InstrumentationExecuteOperationParameters executeOperationParameters = createExecuteOperationParameters(queryComplexityInstrumentation, executionInput, query, schema, state)
        when:
        queryComplexityInstrumentation.beginExecuteOperation(executeOperationParameters, state)
        then:
        def e = thrown(AbortExecutionException)
        e.message == "maximum query complexity exceeded 2 > 1"

    }

    def "custom calculator"() {
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
            {foo {scalar }}
            """)
        def calculator = Mock(FieldComplexityCalculator)
        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(5, calculator)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def state = createInstrumentationState(queryComplexityInstrumentation)
        InstrumentationExecuteOperationParameters executeOperationParameters = createExecuteOperationParameters(queryComplexityInstrumentation, executionInput, query, schema, state)
        when:
        queryComplexityInstrumentation.beginExecuteOperation(executeOperationParameters, state)

        then:
        1 * calculator.calculate({ FieldComplexityEnvironment env -> env.field.name == "scalar" }, 0) >> 10
        1 * calculator.calculate({ FieldComplexityEnvironment env -> env.field.name == "foo" }, 10) >> 20
        def e = thrown(AbortExecutionException)
        e.message == "maximum query complexity exceeded 20 > 5"

    }

    def "custom max query complexity exceeded function"() {
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
            {f2: foo {scalar foo{scalar}} f1: foo { foo {foo {foo {foo{foo{scalar}}}}}} }
            """)
        Boolean customFunctionCalled = false
        Function<QueryComplexityInfo, Boolean> maxQueryComplexityExceededFunction = new Function<QueryComplexityInfo, Boolean>() {
            @Override
            Boolean apply(final QueryComplexityInfo queryComplexityInfo) {
                assert queryComplexityInfo.instrumentationExecuteOperationParameters != null
                assert queryComplexityInfo.instrumentationValidationParameters != null
                customFunctionCalled = true
                return false
            }
        }
        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(10, maxQueryComplexityExceededFunction)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def state = createInstrumentationState(queryComplexityInstrumentation)
        InstrumentationExecuteOperationParameters executeOperationParameters = createExecuteOperationParameters(queryComplexityInstrumentation, executionInput, query, schema, state)
        when:
        queryComplexityInstrumentation.beginExecuteOperation(executeOperationParameters, state)
        then:
        customFunctionCalled
        notThrown(Exception)
    }

    def "complexity with default query variables"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                hello(name: String): String
            }            
        """)
        def query = createQuery("""
            query Hello(\$name:String = "Someone") {
                hello(name: \$name)
            } 
            """)

        MaxQueryComplexityInstrumentation queryComplexityInstrumentation = new MaxQueryComplexityInstrumentation(0)
        ExecutionInput executionInput = Mock(ExecutionInput)
        def state = createInstrumentationState(queryComplexityInstrumentation)
        InstrumentationExecuteOperationParameters executeOperationParameters = createExecuteOperationParameters(queryComplexityInstrumentation, executionInput, query, schema, state)
        when:
        queryComplexityInstrumentation.beginExecuteOperation(executeOperationParameters, state)
        then:
        def e = thrown(AbortExecutionException)
        e.message == "maximum query complexity exceeded 1 > 0"
    }

    private InstrumentationExecuteOperationParameters createExecuteOperationParameters(MaxQueryComplexityInstrumentation queryComplexityInstrumentation, ExecutionInput executionInput, Document query, GraphQLSchema schema, InstrumentationState state) {
        // we need to run N steps to create instrumentation state
        def validationParameters = new InstrumentationValidationParameters(executionInput, query, schema)
        queryComplexityInstrumentation.beginValidation(validationParameters, state)
        def executionContext = executionCtx(executionInput, query, schema)
        def executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext)
        executeOperationParameters
    }

    def createInstrumentationState(MaxQueryComplexityInstrumentation queryComplexityInstrumentation) {
        queryComplexityInstrumentation.createStateAsync(null).join()
    }


    private ExecutionContext executionCtx(ExecutionInput executionInput, Document query, GraphQLSchema schema) {
        ExecutionContextBuilder.newExecutionContextBuilder()
                .executionInput(executionInput).document(query).graphQLSchema(schema).executionId(ExecutionId.generate())
                .build()
    }
}


