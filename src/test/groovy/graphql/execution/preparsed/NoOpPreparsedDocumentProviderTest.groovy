package graphql.execution.preparsed

import graphql.language.Document
import spock.lang.Specification


class NoOpPreparsedDocumentProviderTest extends Specification {
    def "NoOp always returns result of compute function"() {
        given:
        def provider = NoOpPreparsedDocumentProvider.INSTANCE
        def documentEntry = new PreparsedDocumentEntry(Document.newDocument().build())

        when:
        def actual = provider.get("{}", { return documentEntry })

        then:
        actual == documentEntry

    }
}
