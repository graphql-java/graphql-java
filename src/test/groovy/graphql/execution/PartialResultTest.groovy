package graphql.execution

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.NoOpInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring

class PartialResultTest extends Specification {

    DataFetcher slowAndAsyncDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            CompletableFuture.supplyAsync({
                Thread.sleep(60000)
                return "slow"
            })
        }
    }


    def "#460 test partial result can be obtained from instrumentation"() {

        def idl = '''
            type Query {
                field1 : String
                field2 : String
                slowField : String
                bangField : String
            }    
        '''

        Instrumentation instrumentation = new NoOpInstrumentation() {
            @Override
            InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
                if (parameters.field.name.contains("bang")) {
                    throw new AbortExecutionException("Bang")
                }
                return super.beginField(parameters)
            }
        }


        def schema = TestUtil.schema(idl, newRuntimeWiring().type(TypeRuntimeWiring.newTypeWiring("Query")
                .dataFetcher("slowField", slowAndAsyncDF)))

        def graphQL = GraphQL.newGraphQL(schema)
                .instrumentation(instrumentation)
                .queryExecutionStrategy(executionStrategy)
                .build()

        def rootObj = [field1: "hello", field2: "world"]
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query("{ field1, field2, bangField, slowField }")
                .root(rootObj).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == [field1: "hello", field2: "world"]
        result.errors.size() == 1
        result.errors[0].getMessage().contains("Bang")

        where:
        executionStrategy                  | _
        new AsyncSerialExecutionStrategy() | _
        new AsyncExecutionStrategy()       | _
    }

    def "#460 test partial result can be obtained from data fetcher"() {

        def idl = '''
            type Query {
                field1 : String
                field2 : String
                slowField : String
                bangField : String
            }    
        '''

        DataFetcher bangDF = new DataFetcher() {
            Object get(DataFetchingEnvironment environment) {
                throw new AbortExecutionException("Bang!")
            }
        }

        def schema = TestUtil.schema(idl, newRuntimeWiring().type(TypeRuntimeWiring.newTypeWiring("Query")
                .dataFetcher("slowField", slowAndAsyncDF)
                .dataFetcher("bangField", bangDF)
        ))

        def graphQL = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .build()

        def rootObj = [field1: "hello", field2: "world"]
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query("{ field1, field2, bangField, slowField }")
                .root(rootObj).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == [field1: "hello", field2: "world"]
        result.errors.size() == 1
        result.errors[0].getMessage().contains("Bang")

        where:
        executionStrategy                  | _
        new AsyncSerialExecutionStrategy() | _
        new AsyncExecutionStrategy()       | _
    }

}
