package graphql.normalized.nf.provider

import graphql.normalized.nf.NormalizedDocument
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

class NoOpNormalizedDocumentProviderUnitTest extends Specification {
    def "NoOp always returns result of compute function"() {
        given:
        def provider = NoOpNormalizedDocumentProvider.INSTANCE
        def document = new NormalizedDocument(List.of())

        when:
        def actual = provider.getNormalizedDocument(newExecutionInput("{}").build(), { return document })

        then:
        actual.join().document == document
    }
}
