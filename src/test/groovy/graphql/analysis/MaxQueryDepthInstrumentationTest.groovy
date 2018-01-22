package graphql.analysis

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class MaxQueryDepthInstrumentationTest extends Specification {

    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }


    def "doesn't do anything if validation errors occur"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                bar: String
            }
        """)
        def query = createQuery("""
            { bar { thisIsWrong } }
            """)
        def queryTraversal = Mock(QueryTraversal)
        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(6) {

            @Override
            QueryTraversal newQueryTraversal(InstrumentationValidationParameters parameters) {
                return queryTraversal
            }
        }
        ExecutionInput executionInput = Mock(ExecutionInput)
        InstrumentationValidationParameters validationParameters = new InstrumentationValidationParameters(executionInput, query, schema, null)
        InstrumentationContext instrumentationContext = maximumQueryDepthInstrumentation.beginValidation(validationParameters)
        when:
        instrumentationContext.onCompleted([new ValidationError(ValidationErrorType.SubSelectionNotAllowed)], null)
        then:
        0 * queryTraversal._(_)

    }

    def "doesn't do anything if exception was thrown"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                bar: String
            }
        """)
        def query = createQuery("""
            { bar { thisIsWrong } }
            """)
        def queryTraversal = Mock(QueryTraversal)
        MaxQueryDepthInstrumentation maximumQueryDepthInstrumentation = new MaxQueryDepthInstrumentation(6) {

            @Override
            QueryTraversal newQueryTraversal(InstrumentationValidationParameters parameters) {
                return queryTraversal
            }
        }
        ExecutionInput executionInput = Mock(ExecutionInput)
        InstrumentationValidationParameters validationParameters = new InstrumentationValidationParameters(executionInput, query, schema, null)
        InstrumentationContext instrumentationContext = maximumQueryDepthInstrumentation.beginValidation(validationParameters)
        when:
        instrumentationContext.onCompleted(null, new RuntimeException())
        then:
        0 * queryTraversal._(_)

    }

    def "throws exception"() {
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
        InstrumentationValidationParameters validationParameters = new InstrumentationValidationParameters(executionInput, query, schema, null)
        InstrumentationContext instrumentationContext = maximumQueryDepthInstrumentation.beginValidation(validationParameters)
        when:
        instrumentationContext.onCompleted(null, null)
        then:
        def e = thrown(AbortExecutionException)
        e.message.contains("maximum query depth exceeded 7 > 6")
    }

    def "doesn't throw exception"() {
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
        InstrumentationValidationParameters validationParameters = new InstrumentationValidationParameters(executionInput, query, schema, null)
        InstrumentationContext instrumentationContext = maximumQueryDepthInstrumentation.beginValidation(validationParameters)
        when:
        instrumentationContext.onCompleted(null, null)
        then:
        notThrown(Exception)
    }
}
