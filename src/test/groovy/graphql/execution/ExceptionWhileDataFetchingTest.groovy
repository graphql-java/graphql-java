package graphql.execution

import graphql.ErrorType
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLError
import graphql.TestUtil
import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ExceptionWhileDataFetchingTest extends Specification {

    class BangException extends RuntimeException implements GraphQLError {

        def prefix

        BangException(String prefix) {
            this.prefix = prefix
        }

        @Override
        String getMessage() {
            return prefix + "Message"
        }

        @Override
        List<Object> getPath() {
            return ["bang", prefix]
        }

        @Override
        List<SourceLocation> getLocations() {
            return [new SourceLocation(999, 999)]
        }

        @Override
        ErrorType getErrorType() {
            return ErrorType.DataFetchingException
        }

        @Override
        Map<String, Object> getExtensions() {
            return [ext: prefix]
        }
    }

    def "#1018 if an exception is a graphql error, it is transferred correctly"() {
        def spec = '''
            type Query {
                bang : String
                bangCF : String
            }
        '''

        def bangDataFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment env) {
                if (env.getField().getName().contains("CF")) {
                    def cf = new CompletableFuture()
                    cf.completeExceptionally(new BangException("viaCF-"))
                    return cf
                } else {
                    throw new BangException("viaThrow-")
                }
            }
        }


        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                .dataFetcher("bang", bangDataFetcher)
                .dataFetcher("bangCF", bangDataFetcher))
                .build()

        def schema = TestUtil.schema(spec, runtimeWiring)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def executionInput = ExecutionInput.newExecutionInput().query('''
            {
                bang
                bangCF
            }
        ''').build()

        when:
        def result = graphQL.execute(executionInput)

        then:
        result.data == [
                bang  : null,
                bangCF: null
        ]
        result.errors.size() == 2

        //
        // at the moment we only transfer extensions and synthesize a message - we don't transfer
        // path or location because its not really sensible
        //

        result.errors[0].message == "Exception while fetching data (/bang) : viaThrow-Message"
        result.errors[0].path == ["bang"]
        result.errors[0].extensions == [ext: "viaThrow-"]
        result.errors[0] instanceof ExceptionWhileDataFetching

        result.errors[1].message == "Exception while fetching data (/bangCF) : viaCF-Message"
        result.errors[1].path == ["bangCF"]
        result.errors[1].extensions == [ext: "viaCF-"]
        result.errors[1] instanceof ExceptionWhileDataFetching
    }
}
