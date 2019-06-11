package graphql.execution.preparsed


import graphql.language.Document
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

class NoOpPreparsedDocumentProviderTest extends Specification {
    def "NoOp always returns result of compute function"() {
        given:
        def provider = NoOpPreparsedDocumentProvider.INSTANCE
        def documentEntry = new PreparsedDocumentEntry(Document.newDocument().build())

        when:
        def actual = provider.getDocument(newExecutionInput("{}").build(), { return documentEntry })

        then:
        actual == documentEntry

    }
}
