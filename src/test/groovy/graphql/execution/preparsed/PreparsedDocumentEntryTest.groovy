package graphql.execution.preparsed

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
        def document = new Document()

        when:
        def docEntry = new PreparsedDocumentEntry(document)

        then:
        docEntry.document == document
        docEntry.errors == null
    }

    def "Ensure a null document throws NPE"() {
        when:
        new PreparsedDocumentEntry((Document) null)

        then:
        thrown(NullPointerException)
    }

    def "Ensure a non-null errors returns"() {
        given:
        def errors = [new InvalidSyntaxError(new SourceLocation(0, 0), "bang"),
                      new ValidationError(ValidationErrorType.InvalidSyntax)]

        when:
        def docEntry = new PreparsedDocumentEntry(errors)

        then:
        docEntry.document == null
        docEntry.errors == errors
    }

    def "Ensure a null errors throws NPE"() {
        when:
        new PreparsedDocumentEntry((List<GraphQLError>) null)

        then:
        thrown(NullPointerException)
    }

    def "Ensure a null error throws NPE"() {
        when:
        new PreparsedDocumentEntry((GraphQLError) null)

        then:
        thrown(NullPointerException)
    }


}
