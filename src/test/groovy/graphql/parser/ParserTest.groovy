package graphql.parser

import graphql.language.*
import org.antlr.v4.runtime.misc.ParseCancellationException
import spock.lang.Specification
import spock.lang.Unroll

class ParserTest extends Specification {


    def "parse anonymous simple query"() {
        given:
        def input = "{ me }"

        when:
        Document document = new Parser().parseDocument(input)
        then:
        document.definitions.size() == 1
        document.definitions[0] instanceof OperationDefinition
        document.definitions[0].operation == OperationDefinition.Operation.QUERY
        assertField(document.definitions[0] as OperationDefinition, "me")
    }


    def assertField(OperationDefinition operationDefinition, String fieldName) {
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        assert selection instanceof Field
        Field field = (Field) selection
        assert field.name == fieldName
        true
    }


    boolean isEqual(Node node1, Node node2) {
        return new AstComparator().isEqual(node1, node2)
    }

    boolean isEqual(List<Node> node1, List<Node> node2) {
        return new AstComparator().isEqual(node1, node2)
    }

    def "parse selectionSet for field"() {
        given:
        def input = "{ me { name } }"

        def innerSelectionSet = new SelectionSet([new Field("name")])
        def selectionSet = new SelectionSet([new Field("me", innerSelectionSet)])
        def definition = new OperationDefinition(null, OperationDefinition.Operation.QUERY, [], selectionSet)
        def expectedResult = new Document([definition])

        when:
        Document document = new Parser().parseDocument(input)


        then:
        isEqual(document, expectedResult)
    }

    def "parse query with variable definition"() {
        given:
        def input = 'query getProfile($devicePicSize: Int){ me }'

        def expectedResult = new Document()
        def variableDefinition = new VariableDefinition("devicePicSize", new TypeName("Int"))
        def selectionSet = new SelectionSet([new Field("me")])
        def definition = new OperationDefinition("getProfile", OperationDefinition.Operation.QUERY, [variableDefinition], selectionSet)
        expectedResult.definitions.add(definition)

        when:
        Document document = new Parser().parseDocument(input)
        then:
        isEqual(document, expectedResult)
    }

    def "parse mutation"() {
        given:
        def input = 'mutation setName { setName(name: "Homer") { newName } }'

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions[0].operation == OperationDefinition.Operation.MUTATION
    }

    def "parse subscription"() {
        given:
        def input = 'subscription setName { setName(name: "Homer") { newName } }'

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions[0].operation == OperationDefinition.Operation.SUBSCRIPTION
    }

    def "parse field arguments"() {
        given:
        def input = '{ user(id: 10, name: "homer", admin:true, floatValue: 3.04, nullArg : null) }'

        def argument = new Argument("id", new IntValue(10))
        def argument2 = new Argument("name", new StringValue("homer"))
        def argument3 = new Argument("admin", new BooleanValue(true))
        def argument4 = new Argument("floatValue", new FloatValue(3.04))
        def argument5 = new Argument("nullArg", NullValue.Null)
        def field = new Field("user", [argument, argument2, argument3, argument4, argument5])
        def selectionSet = new SelectionSet([field])
        def operationDefinition = new OperationDefinition()
        operationDefinition.operation = OperationDefinition.Operation.QUERY
        operationDefinition.selectionSet = selectionSet
        def expectedResult = new Document([operationDefinition])

        when:
        Document document = new Parser().parseDocument(input)

        then:
        isEqual(document, expectedResult)
    }

    def "parse fragment and query"() {
        given:
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

        and: "expected query"
        def fragmentSpreadFriends = new FragmentSpread("friendFields")
        def selectionSetFriends = new SelectionSet([fragmentSpreadFriends])
        def friendsField = new Field("friends", [new Argument("first", new IntValue(10))], selectionSetFriends)

        def fragmentSpreadMutalFriends = new FragmentSpread("friendFields")
        def selectionSetMutalFriends = new SelectionSet([fragmentSpreadMutalFriends])
        def mutalFriendsField = new Field("mutualFriends", [new Argument("first", new IntValue(10))], selectionSetMutalFriends)

        def userField = new Field("user", [new Argument("id", new IntValue(4))], new SelectionSet([friendsField, mutalFriendsField]))

        def queryDefinition = new OperationDefinition("withFragments", OperationDefinition.Operation.QUERY, new SelectionSet([userField]))

        and: "expected fragment definition"
        def idField = new Field("id")
        def nameField = new Field("name")
        def profilePicField = new Field("profilePic", [new Argument("size", new IntValue(50))])
        def selectionSet = new SelectionSet([idField, nameField, profilePicField])
        def fragmentDefinition = new FragmentDefinition("friendFields", new TypeName("User"), selectionSet)


        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 2
        isEqual(document.definitions[0], queryDefinition)
        isEqual(document.definitions[1], fragmentDefinition)
    }

    def "parse inline fragment"() {
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

        and: "expected query definition"

        def handleField = new Field("handle")

        def userSelectionSet = new SelectionSet([new Field("friends", new SelectionSet([new Field("count")]))])
        def userFragment = new InlineFragment(new TypeName("User"), userSelectionSet)

        def pageSelectionSet = new SelectionSet([new Field("likers", new SelectionSet([new Field("count")]))])
        def pageFragment = new InlineFragment(new TypeName("Page"), pageSelectionSet)


        def handlesArgument = new ArrayValue([new StringValue("zuck"), new StringValue("cocacola")])
        def profilesField = new Field("profiles", [new Argument("handles", handlesArgument)], new SelectionSet([handleField, userFragment, pageFragment]))

        def queryDefinition = new OperationDefinition("InlineFragmentTyping", OperationDefinition.Operation.QUERY,
                new SelectionSet([profilesField]))

        when:
        def document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        isEqual(document.definitions[0], queryDefinition)

    }

    def "parse directives"() {
        given:
        def input = """
            query myQuery(\$someTest: Boolean) {
              experimentalField @skip(if: \$someTest),
              controlField @include(if: \$someTest) }
            """

        and: "expected query"
        def skipDirective = new Directive("skip", [new Argument("if", new VariableReference("someTest"))])
        def experimentalField = new Field("experimentalField", [], [skipDirective])
        def includeDirective = new Directive("include", [new Argument("if", new VariableReference("someTest"))]);
        def controlField = new Field("controlField", [], [includeDirective])

        def queryDefinition = new OperationDefinition("myQuery", OperationDefinition.Operation.QUERY,
                [new VariableDefinition("someTest", new TypeName("Boolean"))],
                new SelectionSet([experimentalField, controlField]))


        when:
        def document = new Parser().parseDocument(input)

        then:
        isEqual(document.definitions[0], queryDefinition)

    }

    @Unroll
    def "parse variable definition for type #typeString"() {
        given:
        def input = """
            query myQuery(\$someTest: $typeString) {
              hello }
            """

        and: "expected query"

        def helloField = new Field("hello")
        def variableDefinition = new VariableDefinition("someTest", getOutputType)
        def queryDefinition = new OperationDefinition("myQuery", OperationDefinition.Operation.QUERY,
                [variableDefinition],
                new SelectionSet([helloField]))


        when:
        def document = new Parser().parseDocument(input)

        then:
        isEqual(document.definitions[0], queryDefinition)

        where:
        typeString    | getOutputType
        "String"      | new TypeName("String")
        "[String]"    | new ListType(new TypeName("String"))
        "Boolean!"    | new NonNullType(new TypeName("Boolean"))
        "[Int]!"      | new NonNullType(new ListType(new TypeName("Int")))
        "[[String!]]" | new ListType(new ListType(new NonNullType(new TypeName("String"))))
    }

    def "parse variable with default value"() {
        given:
        def input = """
            query myQuery(\$variable: String = \"world\") {
              hello }
            """

        and: "expected query"

        def helloField = new Field("hello")
        def variableDefinition = new VariableDefinition("variable", new TypeName("String"), new StringValue("world"))
        def queryDefinition = new OperationDefinition("myQuery", OperationDefinition.Operation.QUERY,
                [variableDefinition],
                new SelectionSet([helloField]))


        when:
        def document = new Parser().parseDocument(input)

        then:
        isEqual(document.definitions[0], queryDefinition)
    }

    def "parse variable with null value"() {
        given:
        def input = """
            query myQuery(\$variable: String = null) {
              hello }
            """

        and: "expected query"

        def helloField = new Field("hello")
        def variableDefinition = new VariableDefinition("variable", new TypeName("String"), NullValue.Null)
        def queryDefinition = new OperationDefinition("myQuery", OperationDefinition.Operation.QUERY,
                [variableDefinition],
                new SelectionSet([helloField]))


        when:
        def document = new Parser().parseDocument(input)

        then:
        isEqual(document.definitions[0], queryDefinition)
    }

    def "parse complex object values"() {
        given:
        def input = """
            query myQuery {
              hello(arg: {intKey:1, floatKey: 4., stringKey: \"world\", subObject: {subKey:true} } ) }
            """

        and: "expected query"

        def objectValue = new ObjectValue()
        objectValue.getObjectFields().add(new ObjectField("intKey", new IntValue(1)))
        objectValue.getObjectFields().add(new ObjectField("floatKey", new FloatValue(4)))
        objectValue.getObjectFields().add(new ObjectField("stringKey", new StringValue("world")))
        def subObject = new ObjectValue()
        subObject.getObjectFields().add(new ObjectField("subKey", new BooleanValue(true)))
        objectValue.getObjectFields().add(new ObjectField("subObject", subObject))
        def argument = new Argument("arg", objectValue)
        def helloField = new Field("hello", [argument])
        def queryDefinition = new OperationDefinition("myQuery", OperationDefinition.Operation.QUERY,
                new SelectionSet([helloField]))


        when:
        def document = new Parser().parseDocument(input)

        then:
        isEqual(document.definitions[0], queryDefinition)
    }

    def "parse complex string value and comment"() {
        given:
        def input = """
            { # this is some comment, which should be captured
               hello(arg: "hello, world" ) }
            """

        when:
        def document = new Parser().parseDocument(input)
        Field helloField = document.definitions[0].selectionSet.selections[0]

        then:
        isEqual(helloField, new Field("hello", [new Argument("arg", new StringValue("hello, world"))]))
        helloField.comments.collect { c -> c.content } == [" this is some comment, which should be captured"]
    }

    @Unroll
    def "parse floatValue #floatString"() {
        given:
        def input = """
            { hello(arg: ${floatString}) }
            """
        when:
        def document = new Parser().parseDocument(input)
        Field helloField = document.definitions[0].selectionSet.selections[0]

        then:
        isEqual(helloField, new Field("hello", [new Argument("arg", new FloatValue(floatValue))]))

        where:
        floatString | floatValue
        '1.0'       | 1.0
        '-0.3'      | -0.3
        '-3.4'      | -3.4
        '-3.4e3'    | -3.4e3
        '3.4E3'     | 3.4e3
        '3e4'       | 3e4
        '123e-4'    | 123e-4

    }

    def "extraneous input is an exception"() {
        given:
        def input = """
        mutation event(\$var: SomeType[]!) { res: update(arg: \$var) {id} }
        """
        when:
        new Parser().parseDocument(input)
        then:
        thrown(ParseCancellationException)
    }

    def "invalid syntax is an error"() {
        given:
        def input = """
        mutation event(() }
        """
        when:
        new Parser().parseDocument(input)
        then:
        thrown(ParseCancellationException)
    }

    def "mutation without a name"() {
        given:
        def input = """
        mutation { m }
        """
        when:
        def document = new Parser().parseDocument(input)
        then:
        document.definitions[0].operation == OperationDefinition.Operation.MUTATION
    }

    def "subscription without a name"(){
        given:
        def input="""
        subscription { s }
        """
        when:
        def document = new Parser().parseDocument(input)
        then:
        document.definitions[0].operation == OperationDefinition.Operation.SUBSCRIPTION

    }

    def "keywords can be used as field names"() {
        given:
        def input = "{ ${name} }"

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        document.definitions[0] instanceof OperationDefinition
        document.definitions[0].operation == OperationDefinition.Operation.QUERY
        assertField(document.definitions[0] as OperationDefinition, name)

        where:
        name           | _
        'fragment'     | _
        'query'        | _
        'mutation'     | _
        'subscription' | _
        'schema'       | _
        'scalar'       | _
        'type'         | _
        'interface'    | _
        'implements'   | _
        'enum'         | _
        'union'        | _
        'input'        | _
        'extend'       | _
        'directive'    | _
    }

    def "#352 - incorrect parentheses are detected"() {
        given:
        def input = "{profile(id:117) {firstNames, lastNames, frontDegree}}}"

        when:
        new Parser().parseDocument(input)

        then:
        def exception = thrown(ParseCancellationException)
        exception != null
    }

    def "#352 - lots of incorrect parentheses are detected"() {
        given:
        def input = "{profile(id:117) {firstNames, lastNames, frontDegree}}}}}}}}"

        when:
        new Parser().parseDocument(input)

        then:
        def exception = thrown(ParseCancellationException)
        exception != null
    }

    def "#352 - comments don't count as unused"() {
        given:
        def input = "{profile(id:117) {firstNames, lastNames, frontDegree}} #trailing comments don't count"

        when:
        new Parser().parseDocument(input)

        then:
        noExceptionThrown()
    }
}
