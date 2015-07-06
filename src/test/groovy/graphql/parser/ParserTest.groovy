package graphql.parser

import graphql.language.Argument
import graphql.language.Document
import graphql.language.Field
import graphql.language.GraphQLType
import graphql.language.IntValue
import graphql.language.NamedType
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.VariableDefinition
import spock.lang.Specification


class ParserTest extends Specification {


    OperationDefinition getOperationDefinition(Document document) {
        ((OperationDefinition) document.definitions[0])
    }

    SelectionSet getRootSelectionSet(Document document) {
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

    def "parse query with variable definition"() {
        given:
        def input = 'query getProfile($devicePicSize: Int){ me }'

        def expectedResult = new Document()
        def variableDefinition = new VariableDefinition("devicePicSize", new NamedType("Int"))
        def selectionSet = new SelectionSet([new Field("me")])
        def definition = new OperationDefinition("getProfile", OperationDefinition.Operation.QUERY, [variableDefinition], selectionSet)
        expectedResult.definitions.add(definition)

        when:
        Document document = new Parser().parseDocument(input)
        println document
        then:
        document == expectedResult

    }

    def "parse mutation"(){
        given:
        def input = 'mutation setName { setName(name: "Homer") { newName } }'

        when:
        Document document = new Parser().parseDocument(input)

        then:
        getOperationDefinition(document).operation == OperationDefinition.Operation.MUTATION
    }

    def "parse field argument"(){
        given:
        def input = '{ user(id: 10) }'

        def argument = new Argument("id",new IntValue(10))
        def field = new Field("user",[argument])
        def selectionSet = new SelectionSet([field])
        def operationDefinition = new OperationDefinition()
        operationDefinition.operation = OperationDefinition.Operation.QUERY
        operationDefinition.selectionSet = selectionSet
        def expectedResult = new Document([operationDefinition])

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document == expectedResult
    }



    Field getInnerField(SelectionSet selectionSet) {
        def field = (Field) selectionSet.selections[0]
        (Field) field.selectionSet.selections[0]
    }


}
