package graphql.parser

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.AstComparator
import graphql.language.BooleanValue
import graphql.language.Description
import graphql.language.Directive
import graphql.language.DirectiveDefinition
import graphql.language.Document
import graphql.language.EnumTypeDefinition
import graphql.language.Field
import graphql.language.FloatValue
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.IgnoredChar
import graphql.language.IgnoredChars
import graphql.language.InlineFragment
import graphql.language.InputObjectTypeDefinition
import graphql.language.IntValue
import graphql.language.InterfaceTypeDefinition
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NodeBuilder
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectValue
import graphql.language.OperationDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.SourceLocation
import graphql.language.StringValue
import graphql.language.Type
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
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
        return AstComparator.isEqual(node1, node2)
    }

    boolean isEqual(List<Node> node1, List<Node> node2) {
        return AstComparator.isEqual(node1, node2)
    }

    def "parse selectionSet for field"() {
        given:
        def input = "{ me { name } }"

        def innerSelectionSet = new SelectionSet([new Field("name")])
        def selectionSet = new SelectionSet([new Field("me", innerSelectionSet)])
        def definition = OperationDefinition.newOperationDefinition().operation(OperationDefinition.Operation.QUERY).selectionSet(selectionSet).build()
        def expectedResult = new Document([definition])

        when:
        Document document = new Parser().parseDocument(input)


        then:
        isEqual(document, expectedResult)
    }

    def "parse query with variable definition"() {
        given:
        def input = 'query getProfile($devicePicSize: Int){ me }'

        def expectedResult = Document.newDocument()
        def variableDefinition = new VariableDefinition("devicePicSize", new TypeName("Int"))
        def selectionSet = new SelectionSet([new Field("me")])
        def definition = OperationDefinition.newOperationDefinition().name("getProfile").operation(OperationDefinition.Operation.QUERY)
                .variableDefinitions([variableDefinition])
                .selectionSet(selectionSet).build()
        expectedResult.definition(definition)

        when:
        Document document = new Parser().parseDocument(input)
        then:
        isEqual(document, expectedResult.build())
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
        def input = '{ user(id: 10, name: "homer", admin:true, floatValue: 3.04) }'

        def argument = new Argument("id", new IntValue(BigInteger.valueOf(10)))
        def argument2 = new Argument("name", new StringValue("homer"))
        def argument3 = new Argument("admin", new BooleanValue(true))
        def argument4 = new Argument("floatValue", new FloatValue(3.04))
        def field = new Field("user", [argument, argument2, argument3, argument4])
        def selectionSet = new SelectionSet([field])
        def operationDefinition = OperationDefinition.newOperationDefinition()
        operationDefinition.operation(OperationDefinition.Operation.QUERY)
        operationDefinition.selectionSet(selectionSet)
        def expectedResult = Document.newDocument().definitions([operationDefinition.build()]).build()

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
        def friendsField = new Field("friends", [new Argument("first", new IntValue(BigInteger.valueOf(10)))], selectionSetFriends)

        def fragmentSpreadMutalFriends = new FragmentSpread("friendFields")
        def selectionSetMutalFriends = new SelectionSet([fragmentSpreadMutalFriends])
        def mutalFriendsField = new Field("mutualFriends", [new Argument("first", new IntValue(BigInteger.valueOf(10)))], selectionSetMutalFriends)

        def userField = new Field("user", [new Argument("id", new IntValue(BigInteger.valueOf(4)))], new SelectionSet([friendsField, mutalFriendsField]))

        def queryDefinition = OperationDefinition.newOperationDefinition().name("withFragments").operation(OperationDefinition.Operation.QUERY).selectionSet(new SelectionSet([userField])).build()

        and: "expected fragment definition"
        def idField = new Field("id")
        def nameField = new Field("name")
        def profilePicField = new Field("profilePic", [new Argument("size", new IntValue(BigInteger.valueOf(50)))])
        def selectionSet = SelectionSet.newSelectionSet().selections([idField, nameField, profilePicField]).build()
        def fragmentDefinition = FragmentDefinition.newFragmentDefinition().name("friendFields").typeCondition(new TypeName("User")).selectionSet(selectionSet).build()


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

        def queryDefinition = OperationDefinition.newOperationDefinition()
                .name("InlineFragmentTyping").operation(OperationDefinition.Operation.QUERY)
                .selectionSet(new SelectionSet([profilesField])).build()

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
        def experimentalField = Field.newField().name("experimentalField").directives([skipDirective]).build()
        def includeDirective = new Directive("include", [new Argument("if", new VariableReference("someTest"))])
        def controlField = Field.newField().name("controlField").directives([includeDirective]).build()

        def queryDefinition = OperationDefinition.newOperationDefinition()
                .name("myQuery")
                .operation(OperationDefinition.Operation.QUERY)
                .variableDefinitions([new VariableDefinition("someTest", new TypeName("Boolean"))])
                .selectionSet(new SelectionSet([experimentalField, controlField])).build()
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
        def variableDefinition = new VariableDefinition("someTest", getOutputType as Type)
        def queryDefinition = OperationDefinition.newOperationDefinition().name("myQuery").operation(OperationDefinition.Operation.QUERY)
                .variableDefinitions([variableDefinition]).selectionSet(new SelectionSet([helloField])).build()


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
        def queryDefinition = OperationDefinition.newOperationDefinition().name("myQuery").operation(OperationDefinition.Operation.QUERY)
                .variableDefinitions([variableDefinition]).selectionSet(new SelectionSet([helloField])).build()


        when:
        def document = new Parser().parseDocument(input)

        then:
        isEqual(document.definitions[0], queryDefinition)
    }

    def "parse complex object values"() {
        given:
        def input = """
            query myQuery {
              hello(arg: {intKey:1, floatKey: 4.1, stringKey: \"world\", subObject: {subKey:true} } ) }
            """

        and: "expected query"

        def objectValue = ObjectValue.newObjectValue()
        objectValue.objectField(new ObjectField("intKey", new IntValue(BigInteger.valueOf(1))))
        objectValue.objectField(new ObjectField("floatKey", new FloatValue(4.1)))
        objectValue.objectField(new ObjectField("stringKey", new StringValue("world")))
        def subObject = ObjectValue.newObjectValue()
        subObject.objectField(new ObjectField("subKey", new BooleanValue(true)))
        objectValue.objectField(new ObjectField("subObject", subObject.build()))
        def argument = new Argument("arg", objectValue.build())
        def helloField = new Field("hello", [argument])
        def queryDefinition = OperationDefinition.newOperationDefinition().name("myQuery")
                .operation(OperationDefinition.Operation.QUERY)
                .selectionSet(new SelectionSet([helloField])).build()


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
        Field helloField = (document.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

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
        Field helloField = (document.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

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

    def "#848 floats must have digits"() {
        given:
        def input = """
            { hello(arg: 4.) }
            """
        when:
        new Parser().parseDocument(input)

        then:
        thrown(InvalidSyntaxException)
    }

    def "extraneous input is an exception"() {
        given:
        def input = """
        mutation event(\$var: SomeType[]!) { res: update(arg: \$var) {id} }
        """
        when:
        new Parser().parseDocument(input)
        then:
        thrown(InvalidSyntaxException)
    }

    def "invalid syntax is an error"() {
        given:
        def input = """
        mutation event(() }
        """
        when:
        new Parser().parseDocument(input)
        then:
        thrown(InvalidSyntaxException)
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

    def "subscription without a name"() {
        given:
        def input = """
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
        def exception = thrown(InvalidSyntaxException)
        exception != null
    }

    def "#352 - lots of incorrect parentheses are detected"() {
        given:
        def input = "{profile(id:117) {firstNames, lastNames, frontDegree}}}}}}}}"

        when:
        new Parser().parseDocument(input)

        then:
        def exception = thrown(InvalidSyntaxException)
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


    def "parses null values"() {
        given:
        def input = "{ foo(bar: null, bell : null) }"

        when:
        def document = new Parser().parseDocument(input)
        def operation = document.definitions[0] as OperationDefinition
        def selection = operation.selectionSet.selections[0] as Field

        then:
        selection.arguments[0].value instanceof NullValue
        selection.arguments[1].value instanceof NullValue

        selection.arguments[0].value.sourceLocation.toString() == "SourceLocation{line=1, column=12}"
        selection.arguments[1].value.sourceLocation.toString() == "SourceLocation{line=1, column=25}"

    }


    def "whitespace_ignored"() {
        given:
        def BOM = "\ufeff"
        def ws = "\t \n"
        def comma = ","
        def input = "{ " + BOM + ws + comma + "foo(bar: null) }"

        when:
        def document = new Parser().parseDocument(input)
        def operation = document.definitions[0] as OperationDefinition
        def selection = operation.selectionSet.selections[0] as Field

        then:
        selection.name == "foo"
    }

    def "four quotation marks is an illegal string"() {
        given:
        def input = '''{foo(arg:[""""])}'''

        when:
        Parser.parse(input)

        then:
        def e = thrown(InvalidSyntaxException)
        e.message.contains("Invalid Syntax")
    }

    def "three quotation marks is an illegal string"() {
        given:
        def input = '''{foo(arg: ["""])}'''

        when:
        Parser.parse(input)

        then:
        def e = thrown(InvalidSyntaxException)
        e.message.contains("Invalid Syntax")
    }

    def "escaped triple quote inside block string"() {
        given:
        def input = '''{foo(arg: """\\"""""")}'''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = document.definitions[0] as OperationDefinition
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        Field field = (Field) selection

        then:
        field.getArguments().size() == 1
        argValue(field, 0) == '"""'
    }

    def "triple quoted strings"() {
        given:
        def input = '''{ field(triple : """triple
string""", single : "single") }'''

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        OperationDefinition operationDefinition = document.definitions[0] as OperationDefinition
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        Field field = (Field) selection
        assert field.getArguments().size() == 2
        assert argValue(field, 0) == 'triple\nstring'
        assert argValue(field, 1) == 'single'
    }

    def "triple quoted strings with the one special escape character"() {
        given:
        def input = '''{ field(
triple : """triple

string that is \\""" escaped""", 

triple2 : """another string with \\""" escaping""", 

triple3 : """edge cases \\""" "" " \\"" \\" edge cases"""

) }'''

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 1
        OperationDefinition operationDefinition = document.definitions[0] as OperationDefinition
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        Field field = (Field) selection
        assert field.getArguments().size() == 3
        assert argValue(field, 0) == 'triple\n\nstring that is """ escaped'
        assert argValue(field, 1) == 'another string with """ escaping'
        assert argValue(field, 2) == 'edge cases """ "" " \\"" \\" edge cases'
    }

    String argValue(Field field, Integer index) {
        def value = (field.getArguments()[index].getValue() as StringValue).getValue()
        value
    }

    def "parse IDL type definitions with doc string comments"() {
        given:
        def input = '''
            """object type"""
            type Object {
                """object field"""
                field : String
            }
            
            """interface type"""
            interface Interface {
                """interface field"""
                field : String
            }
            
            """union type"""
            union Union = Foo | Bar
            
            """scalar type"""
            scalar Scalar
            
            """enum type"""
            enum Enum {
                """enum field"""
                foo
            }
            
            """input type"""
            input Input {
                """input field"""
                field : String
            }

            """directive def""" 
            directive @ dname on scalar            

'''

        when:
        Document document = new Parser().parseDocument(input)

        then:
        document.definitions.size() == 7

        assertMultiDesc((document.definitions[0] as ObjectTypeDefinition).getDescription(), "object type")
        assertMultiDesc((document.definitions[0] as ObjectTypeDefinition).getFieldDefinitions()[0].getDescription(), "object field")

        assertMultiDesc((document.definitions[1] as InterfaceTypeDefinition).getDescription(), "interface type")
        assertMultiDesc((document.definitions[1] as InterfaceTypeDefinition).getFieldDefinitions()[0].getDescription(), "interface field")

        assertMultiDesc((document.definitions[2] as UnionTypeDefinition).getDescription(), "union type")

        assertMultiDesc((document.definitions[3] as ScalarTypeDefinition).getDescription(), "scalar type")

        assertMultiDesc((document.definitions[4] as EnumTypeDefinition).getDescription(), "enum type")
        assertMultiDesc((document.definitions[4] as EnumTypeDefinition).getEnumValueDefinitions()[0].getDescription(), "enum field")

        assertMultiDesc((document.definitions[5] as InputObjectTypeDefinition).getDescription(), "input type")
        assertMultiDesc((document.definitions[5] as InputObjectTypeDefinition).getInputValueDefinitions()[0].getDescription(), "input field")

        assertMultiDesc((document.definitions[6] as DirectiveDefinition).getDescription(), "directive def")

    }

    def assertMultiDesc(Description description, String expected) {
        assertDesc(description, expected, true)
    }

    def assertDesc(Description description, String expected, boolean multiLine) {
        if (description == null) {
            assert expected == null
        } else {
            assert expected == description.getContent(), "content check"
            assert multiLine == description.isMultiLine(), "multi line ==" + multiLine
        }
        true
    }


    def "parse ignored chars"() {
        given:
        def input = "{,\r me\n\t} ,\n"

        when:
        def captureIgnoredCharsTRUE = ParserOptions.newParserOptions().captureIgnoredChars(true).build()

        Document document = new Parser().parseDocument(input, captureIgnoredCharsTRUE)
        def field = (document.definitions[0] as OperationDefinition).selectionSet.selections[0]
        then:
        field.getIgnoredChars().getLeft().size() == 3
        field.getIgnoredChars().getLeft()[0] == new IgnoredChar(",", IgnoredChar.IgnoredCharKind.COMMA, new SourceLocation(1, 2))
        field.getIgnoredChars().getLeft()[1] == new IgnoredChar("\r", IgnoredChar.IgnoredCharKind.CR, new SourceLocation(1, 3))
        field.getIgnoredChars().getLeft()[2] == new IgnoredChar(" ", IgnoredChar.IgnoredCharKind.SPACE, new SourceLocation(1, 4))

        field.getIgnoredChars().getRight().size() == 2
        field.getIgnoredChars().getRight()[0] == new IgnoredChar("\n", IgnoredChar.IgnoredCharKind.LF, new SourceLocation(1, 7))
        field.getIgnoredChars().getRight()[1] == new IgnoredChar("\t", IgnoredChar.IgnoredCharKind.TAB, new SourceLocation(2, 1))

        document.getIgnoredChars().getRight().size() == 3
        document.getIgnoredChars().getRight()[0] == new IgnoredChar(" ", IgnoredChar.IgnoredCharKind.SPACE, new SourceLocation(2, 3))
        document.getIgnoredChars().getRight()[1] == new IgnoredChar(",", IgnoredChar.IgnoredCharKind.COMMA, new SourceLocation(2, 4))
        document.getIgnoredChars().getRight()[2] == new IgnoredChar("\n", IgnoredChar.IgnoredCharKind.LF, new SourceLocation(2, 5))
    }

    def "parsed float with positive exponent"() {
        given:
        def input = """
            {
                getEmployee (sal:1.7976931348155E+308){
                    sal
                }
            }
        """
        when:
        Document document = new Parser().parseDocument(input)
        Field getEmployee = (document.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field
        def argumentValue = getEmployee.getArguments().get(0).getValue()

        then:
        argumentValue instanceof FloatValue
        ((FloatValue) argumentValue).value.toString() == "1.7976931348155E+308"
    }

    def "parse fragment definition"() {
        given:
        def input = """
            fragment Foo on Bar {
                hello
            }
        """
        when:
        Document document = Parser.parse(input)
        FragmentDefinition fragmentDefinition = (document.definitions[0] as FragmentDefinition)

        then:
        fragmentDefinition.name == "Foo"

    }

    def "parser should throw syntax errors"() {
        given:
        def input = """
            type Foo {
              name / String
            }
        """
        when:
        def document = Parser.parse(input)
        println document
        then:
        def e = thrown(InvalidSyntaxException)
        e.message.contains("Invalid Syntax")
        e.sourcePreview == input + "\n"
        e.location.line == 3
        e.location.column == 20
    }

    def "allow emoji in comments"() {
        def input = '''
              # Represents the 😕 emoji.
              {
              foo
               }
    '''
        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)


        then:
        operationDefinition.getComments()[0].content == " Represents the 😕 emoji."
    }

    def "can override antlr to ast"() {

        def query = '''
            query {
                field
            }
        '''
        when:
        Parser parser = new Parser() {
            @Override
            protected GraphqlAntlrToLanguage getAntlrToLanguage(CommonTokenStream tokens, MultiSourceReader multiSourceReader) {
                // this pattern is used in Nadel - its backdoor but needed
                return new GraphqlAntlrToLanguage(tokens, multiSourceReader) {
                    @Override
                    protected void addCommonData(NodeBuilder nodeBuilder, ParserRuleContext parserRuleContext) {
                        super.addCommonData(nodeBuilder, parserRuleContext)
                        nodeBuilder.additionalData("key", "value")
                    }
                }
            }
        }

        def document = parser.parseDocument(query)

        then:
        document.getAdditionalData().get("key") == "value"
        document.children[0].getAdditionalData().get("key") == "value"

        when: "The new override method is used"
        parser = new Parser() {

            @Override
            protected GraphqlAntlrToLanguage getAntlrToLanguage(CommonTokenStream tokens, MultiSourceReader multiSourceReader, ParserOptions parserOptions) {
                return new GraphqlAntlrToLanguage(tokens, multiSourceReader, parserOptions) {
                    @Override
                    protected void addCommonData(NodeBuilder nodeBuilder, ParserRuleContext parserRuleContext) {
                        super.addCommonData(nodeBuilder, parserRuleContext)
                        nodeBuilder.additionalData("key", "value")
                    }
                }
            }
        }

        document = parser.parseDocument(query)

        then:
        document.getAdditionalData().get("key") == "value"
        document.children[0].getAdditionalData().get("key") == "value"
    }

    def "parse integer"() {
        given:
        def input = '''{foo(arg: 11)}'''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = document.definitions[0] as OperationDefinition
        Selection selection = operationDefinition.getSelectionSet().getSelections()[0]
        Field field = (Field) selection

        then:
        field.getArguments().size() == 1
        (field.getArguments()[0].getValue() as IntValue).getValue().intValueExact() == 11
    }

    @Unroll
    def "invalid int #value is an error"() {
        given:
        def input = "{foo(arg: [$value])}"

        when:
        Parser.parse(input)

        then:
        def e = thrown(InvalidSyntaxException)
        e.message.contains("Invalid Syntax")
        where:
        value  | _
        '00'   | _
        '01'   | _
        '123.' | _
        '123e' | _
        '123E' | _
    }

    @Unroll
    def "invalid float #value is an error"() {
        given:
        def input = "{foo(arg: [$value])}"

        when:
        Parser.parse(input)

        then:
        def e = thrown(InvalidSyntaxException)
        e.message.contains("Invalid Syntax")
        where:
        value     | _
        '01.23'   | _
        '1.2e3.4' | _
        '1.23.4'  | _
        '1.2e3e'  | _
    }

    @Unroll
    def 'parse ast literals #valueLiteral'() {
        expect:
        Parser.parseValue(valueLiteral) in expectedValue

        where:
        valueLiteral                                  | expectedValue
        '"s"'                                         | StringValue.class
        'true'                                        | BooleanValue.class
        '666'                                         | IntValue.class
        '666.6'                                       | FloatValue.class
        '["A", "B", "C"]'                             | ArrayValue.class
        '{string : "s", integer : 1, boolean : true}' | ObjectValue.class
    }

    def "ignored chars can be set on or off"() {
        def s = '''
            
               type X    {
            s : String
            }
        '''

        def captureIgnoredCharsFALSE = ParserOptions.newParserOptions().captureIgnoredChars(false).build()
        def captureIgnoredCharsTRUE = ParserOptions.newParserOptions().captureIgnoredChars(true).build()

        when: "explicitly off"
        def doc = new Parser().parseDocument(s, captureIgnoredCharsFALSE)
        def type = doc.getDefinitionsOfType(ObjectTypeDefinition)[0]
        then:
        type.getIgnoredChars() == IgnoredChars.EMPTY

        when: "implicitly off it uses the system default"
        doc = new Parser().parseDocument(s)
        type = doc.getDefinitionsOfType(ObjectTypeDefinition)[0]

        then:
        type.getIgnoredChars() == IgnoredChars.EMPTY
        !ParserOptions.getDefaultParserOptions().isCaptureIgnoredChars()

        when: "explicitly on"

        doc = new Parser().parseDocument(s, captureIgnoredCharsTRUE)
        type = doc.getDefinitionsOfType(ObjectTypeDefinition)[0]

        then:
        type.getIgnoredChars() != IgnoredChars.EMPTY
        !type.getIgnoredChars().getLeft().isEmpty()
        !type.getIgnoredChars().getRight().isEmpty()


        when: "implicitly on if the static is set"
        ParserOptions.setDefaultParserOptions(captureIgnoredCharsTRUE)
        doc = new Parser().parseDocument(s)
        type = doc.getDefinitionsOfType(ObjectTypeDefinition)[0]

        then:
        type.getIgnoredChars() != IgnoredChars.EMPTY
        !type.getIgnoredChars().getLeft().isEmpty()
        !type.getIgnoredChars().getRight().isEmpty()
    }

    def "allow braced escaped unicode"() {
        given:
        def input = '''
              {
              foo(arg: "\\u{1F37A}")
               }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)
        def field = operationDefinition.getSelectionSet().getSelections()[0] as Field
        def argValue = field.arguments[0].value as StringValue

        then:
        argValue.getValue() == "🍺" // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "allow surrogate pairs escaped unicode"() {
        given:
        def input = '''
              {
              foo(arg: "\\ud83c\\udf7a")
               }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)
        def field = operationDefinition.getSelectionSet().getSelections()[0] as Field
        def argValue = field.arguments[0].value as StringValue

        then:
        argValue.getValue() == "🍺" // contains the beer icon U+1F37 A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "invalid surrogate pair - no trailing value"() {
        given:
        def input = '''
              {
              foo(arg: "\\ud83c")
               }
        '''

        when:
        Parser.parse(input)

        then:
        InvalidSyntaxException e = thrown(InvalidSyntaxException)
        e.message == "Invalid Syntax : Invalid unicode - leading surrogate must be followed by a trailing surrogate - offending token '\\ud83c' at line 3 column 24"
    }

    def "invalid surrogate pair - no leading value"() {
        given:
        def input = '''
              {
              foo(arg: "\\uDC00")
               }
        '''

        when:
        Parser.parse(input)

        then:
        InvalidSyntaxException e = thrown(InvalidSyntaxException)
        e.message == "Invalid Syntax : Invalid unicode - trailing surrogate must be preceded with a leading surrogate - offending token '\\uDC00' at line 3 column 24"
    }

    def "source locations are on by default but can be turned off"() {
        when:
        def options = ParserOptions.getDefaultParserOptions()

        def document = new Parser().parseDocument("{ f }")
        then:
        options.isCaptureSourceLocation()
        document.getSourceLocation() == new SourceLocation(1, 1)
        document.getDefinitions()[0].getSourceLocation() == new SourceLocation(1, 1)

        when:
        options = ParserOptions.newParserOptions().captureSourceLocation(false).build()
        document = new Parser().parseDocument("{ f }", options)

        then:
        !options.isCaptureSourceLocation()
        document.getSourceLocation() == SourceLocation.EMPTY
        document.getDefinitions()[0].getSourceLocation() == SourceLocation.EMPTY
    }

    def "a billion laughs attack will be prevented by default"() {
        def lol = "@lol" * 10000 // two tokens = 20000+ tokens
        def text = "query { f $lol }"
        when:
        Parser.parse(text)

        then:
        def e = thrown(ParseCancelledException)
        e.getMessage().contains("parsing has been cancelled")

        when: "integration test to prove it cancels by default"

        def sdl = """type Query { f : ID} """
        def graphQL = TestUtil.graphQL(sdl).build()
        def er = graphQL.execute(text)
        then:
        er.errors.size() == 1
        er.errors[0].message.contains("parsing has been cancelled")
    }

    def "they can shoot themselves if they want to with large documents"() {
        def lol = "@lol" * 10000 // two tokens = 20000+ tokens
        def text = "query { f $lol }"

        def options = ParserOptions.newParserOptions().maxTokens(30000).build()
        when:
        def doc = new Parser().parseDocument(text, options)

        then:
        doc != null
    }

    def "they can set their own listener into action"() {
        def queryText = "query { f(arg : 1) }"

        def count = 0
        def tokens = []
        ParsingListener listener = { count++; tokens.add(it.getText()) }
        def parserOptions = ParserOptions.newParserOptions().parsingListener(listener).build()
        when:
        def doc = new Parser().parseDocument(queryText, parserOptions)

        then:
        doc != null
        count == 9
        tokens == ["query" , "{", "f" , "(", "arg", ":", "1", ")", "}"]

        when: "integration test to prove it be supplied via EI"

        def sdl = """type Query { f(arg : Int) : ID} """
        def graphQL = TestUtil.graphQL(sdl).build()


        def context = [:]
        context.put(ParserOptions.class, parserOptions)
        def executionInput = ExecutionInput.newExecutionInput()
                .query(queryText)
                .graphQLContext(context).build()

        count = 0
        tokens = []
        def er = graphQL.execute(executionInput)
        then:
        er.errors.size() == 0
        count == 9
        tokens == ["query" , "{", "f" , "(", "arg", ":", "1", ")", "}"]

    }
}
