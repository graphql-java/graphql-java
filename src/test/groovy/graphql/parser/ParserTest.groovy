package graphql.parser

import graphql.language.Argument
import graphql.language.BooleanValue
import graphql.language.Document
import graphql.language.Field
import graphql.language.FloatValue
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.GraphQLType
import graphql.language.IntValue
import graphql.language.NamedType
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.StringValue
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

    def "parse mutation"() {
        given:
        def input = 'mutation setName { setName(name: "Homer") { newName } }'

        when:
        Document document = new Parser().parseDocument(input)

        then:
        getOperationDefinition(document).operation == OperationDefinition.Operation.MUTATION
    }

    def "parse field arguments"() {
        given:
        def input = '{ user(id: 10 name: "homer" admin:true floatValue: 3.04) }'

        def argument = new Argument("id", new IntValue(10))
        def argument2 = new Argument("name", new StringValue("homer"))
        def argument3 = new Argument("admin", new BooleanValue(true))
        def argument4 = new Argument("floatValue", new FloatValue(3.04))
        def field = new Field("user", [argument, argument2, argument3, argument4])
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

    def "parse fragment and query"() {
        def input = """query withFragments {
                    user(id: 4) {
                        friends(first: 10) { ...friendFields }
                        mutualFriends(first: 10) { ...friendFields }
                      }
                    }

                    fragment friendFields on User {
                      id
                      name
                    profilePic(size: 50)
                }"""

        when:
        Document document = new Parser().parseDocument(input)

        def idField = new Field("id")
        def nameField = new Field("name")
        def profilePicField = new Field("profilePic", [new Argument("size", new IntValue(50))])
        def selectionSet = new SelectionSet([idField, nameField, profilePicField])
        def fragmentDefinition = new FragmentDefinition("friendFields", "User", selectionSet)

        def fragmentSpreadFriends = new FragmentSpread("friendFields")
        def selectionSetFriends = new SelectionSet([fragmentSpreadFriends])
        def friendsField = new Field("friends", [new Argument("first", new IntValue(10))], selectionSetFriends)

        def fragmentSpreadMutalFriends = new FragmentSpread("friendFields")
        def selectionSetMutalFriends = new SelectionSet([fragmentSpreadMutalFriends])
        def mutalFriendsField = new Field("mutualFriends", [new Argument("first", new IntValue(10))], selectionSetMutalFriends)

        def userField = new Field("user", [new Argument("id", new IntValue(4))], new SelectionSet([friendsField, mutalFriendsField]))

        def queryDefinition = new OperationDefinition("withFragments", OperationDefinition.Operation.QUERY, new SelectionSet([userField]))

        then:
        document.definitions.size() == 2
        document.definitions[0] == queryDefinition
        document.definitions[1] == fragmentDefinition
    }

    def "parse inline fragment"(){
        given:
        def input = """
                    query InlineFragmentTyping {
                      profiles(handles: ["zuck", "cocacola"]) {
                        handle
                        ... on User {
                          friends { count }
                        }
                        ... on Page {
                          likers { count }
                        }
                      }
                    }
                """


        when:
        def document = new Parser().parseDocument(input)


        then:
        document.definitions.size() == 1

    }


    Field getInnerField(SelectionSet selectionSet) {
        def field = (Field) selectionSet.selections[0]
        (Field) field.selectionSet.selections[0]
    }


}
