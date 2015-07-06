package graphql.parser

import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import spock.lang.Specification


class ParserTest extends Specification {


    def "parse anonymous simple query"() {
        given:
        def input = "{ me }"

        when:
        Document document = new Parser().parseDocument(input)
        then:
        document.definitions.size() == 1
        document.definitions[0] instanceof OperationDefinition
        getOperationDefinition(document).operation == OperationDefinition.Operation.QUERY
        assertField(getOperationDefinition(document), "me")
    }

    OperationDefinition getOperationDefinition(Document document) {
        ((OperationDefinition) document.definitions[0])
    }

    def assertField(OperationDefinition operationDefinition, String fieldName) {
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        assert selection instanceof Field
        Field field = (Field) selection
        assert field.name == fieldName
        true
    }

}
