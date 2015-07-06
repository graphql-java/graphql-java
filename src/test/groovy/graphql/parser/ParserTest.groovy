package graphql.parser

import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import spock.lang.Specification


class ParserTest extends Specification {


    OperationDefinition getOperationDefinition(Document document) {
        ((OperationDefinition) document.definitions[0])
    }

    SelectionSet getRootSelectionSet(Document document){
        getOperationDefinition(document).selectionSet
    }



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


    def assertField(OperationDefinition operationDefinition, String fieldName) {
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        assert selection instanceof Field
        Field field = (Field) selection
        assert field.name == fieldName
        true
    }


    def "parse selectionSet for field"() {
        given:
        def input = "{ me { name } }"

        when:
        Document document = new Parser().parseDocument(input)
        def rootSelectionSet = getRootSelectionSet(document)

        then:
        getInnerField(rootSelectionSet).name == "name"
    }

    Field getInnerField(SelectionSet selectionSet) {
        def field = (Field) selectionSet.selections[0]
        (Field) field.selectionSet.selections[0]
    }


}
