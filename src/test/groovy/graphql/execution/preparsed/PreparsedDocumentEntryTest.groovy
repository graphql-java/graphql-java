package graphql.execution.preparsed

import graphql.AssertException
import graphql.GraphQLError
import graphql.InvalidSyntaxError
import graphql.language.Document
import graphql.language.SourceLocation
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class PreparsedDocumentEntryTest extends Specification {
    def "Ensure a non-null document returns"() {
        given:
        def document = Document.newDocument().build()

        when:
        def docEntry = new PreparsedDocumentEntry(document)

        then:
        docEntry.document == document
        docEntry.errors == null
    }

    def "Ensure a null document throws Exception"() {
        when:
        new PreparsedDocumentEntry((Document) null)

        then:
        thrown(AssertException)
    }

    def "Ensure a non-null errors returns"() {
        given:
        def errors = [new InvalidSyntaxError(new SourceLocation(0, 0), "bang"),
                      ValidationError.newValidationError().validationErrorType(ValidationErrorType.InvalidSyntax).description("Invalid syntax in document").build()]

        when:
        def docEntry = new PreparsedDocumentEntry(errors)

        then:
        docEntry.document == null
        docEntry.errors == errors
    }

    def "Ensure a non-null error returns"() {
        given:
        def error = new InvalidSyntaxError(new SourceLocation(0, 0), "bang")

        when:
        def docEntry = new PreparsedDocumentEntry(error)

        then:
        docEntry.document == null
        docEntry.errors == Collections.singletonList(error)
    }

    def "Ensure a null error throws Exception"() {
        when:
        new PreparsedDocumentEntry((GraphQLError) null)

        then:
        thrown(AssertException)
    }

    def "Ensure a null errors throws Exception"() {
        when:
        new PreparsedDocumentEntry((List<GraphQLError>) null)

        then:
        thrown(AssertException)
    }

    def "Ensure a null error and valid document throws Exception"() {
        given:
        def document = Document.newDocument().build()

        when:
        new PreparsedDocumentEntry(document, (List<GraphQLError>) null)

        then:
        thrown(AssertException)
    }

    def "Ensure a non-null error and null document throws Exception"() {
        given:
        def error = new InvalidSyntaxError(new SourceLocation(0, 0), "bang")

        when:
        new PreparsedDocumentEntry(null, [error])

        then:
        thrown(AssertException)
    }

    def "Ensure a non-null error and non-null document returns"() {
        given:
        def error = new InvalidSyntaxError(new SourceLocation(0, 0), "bang")
        def document = Document.newDocument().build()

        when:
        def docEntry = new PreparsedDocumentEntry(document, [error])

        then:
        docEntry.document == document
        docEntry.errors.get(0) == error
    }
}
