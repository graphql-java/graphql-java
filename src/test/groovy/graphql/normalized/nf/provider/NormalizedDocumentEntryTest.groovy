package graphql.normalized.nf.provider

import graphql.AssertException
import graphql.normalized.nf.NormalizedDocument
import spock.lang.Specification

class NormalizedDocumentEntryTest extends Specification {
    def "Ensure a non-null document returns"() {
        given:
        def document = new NormalizedDocument(List.of())

        when:
        def entry = new NormalizedDocumentEntry(document)

        then:
        entry.document == document
    }

    def "Ensure a null document throws Exception"() {
        when:
        new NormalizedDocumentEntry((NormalizedDocument) null)

        then:
        thrown(AssertException)
    }
}
