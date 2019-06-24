package graphql

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class Issue1559 extends Specification {

    def graphql = TestUtil.graphQL("""
                type Query {
                    contextAwareList: [ContextAwareEntity!]!
                }
                
                type ContextAwareEntity {
                    name: String
                    contextInfo: String
                }
                
            """,
            RuntimeWiring.newRuntimeWiring()
                .type("ContextAwareEntity", {
                    it.dataFetcher("contextInfo", { it.getLocalContext() })
                })
                .build())
        .build()

    def "#1559 test if a list of DataFetcherResults is processed properly"() {

        when:
        def input = ExecutionInput.newExecutionInput()
                .root([contextAwareList: [
                        DataFetcherResult.newResult().localContext("the context 1").data([name: "the name 1"]).build(),
                        DataFetcherResult.newResult().localContext("the context 2").data([name: "the name 2"]).build(),
                        DataFetcherResult.newResult().localContext("the context 3").data([name: "the name 3"]).build(),
                        DataFetcherResult.newResult().localContext("the context 4").data([name: "the name 4"]).build(),
                ]])
                .query('''
                        query getTheList {
                            contextAwareList {
                                name
                                contextInfo
                            }
                        }
                        ''')
                .build()
        def executionResult = graphql.execute(input)

        then:
        executionResult.errors.isEmpty()
        executionResult.data == [ contextAwareList: [
                [name: "the name 1", contextInfo: "the context 1"],
                [name: "the name 2", contextInfo: "the context 2"],
                [name: "the name 3", contextInfo: "the context 3"],
                [name: "the name 4", contextInfo: "the context 4"],
        ]]

    }
    def "#1559 test if null validations are processed properly on DataFetcherResult List"() {

        when:
        def input = ExecutionInput.newExecutionInput()
                .root([contextAwareList: [
                        DataFetcherResult.newResult().localContext("the context 1").data([name: "the name 1"]).build(),
                        DataFetcherResult.newResult().localContext("the context 2").data(null).build(),
                        null,
                        DataFetcherResult.newResult().localContext("the context 4").data([name: "the name 4"]).build(),
                ]])
                .query('''
                        query getTheList {
                            contextAwareList {
                                name
                                contextInfo
                            }
                        }
                        ''')
                .build()
        def executionResult = graphql.execute(input)

        then:
        executionResult.errors.size() == 2
        executionResult.errors[0].path == ['contextAwareList', 1]
        executionResult.errors[1].path == ['contextAwareList', 2]
    }

}
