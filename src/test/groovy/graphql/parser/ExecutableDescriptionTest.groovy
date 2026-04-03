package graphql.parser

import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.language.VariableDefinition
import spock.lang.Specification

class ExecutableDescriptionTest extends Specification {

    def "parse operation definition with description"() {
        given:
        def input = '''
            "My query description"
            query MyQuery {
                foo
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition

        then:
        op.description != null
        op.description.content == "My query description"
        !op.description.multiLine
        op.name == "MyQuery"
        op.operation == OperationDefinition.Operation.QUERY
    }

    def "parse operation definition with multiline description"() {
        given:
        def input = '''
            """
            My multiline
            query description
            """
            mutation DoSomething {
                doIt
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition

        then:
        op.description != null
        op.description.content.contains("My multiline")
        op.description.content.contains("query description")
        op.description.multiLine
        op.operation == OperationDefinition.Operation.MUTATION
    }

    def "parse fragment definition with description"() {
        given:
        def input = '''
            "My fragment description"
            fragment MyFragment on User {
                name
            }
        '''

        when:
        Document document = Parser.parse(input)
        FragmentDefinition frag = document.definitions[0] as FragmentDefinition

        then:
        frag.description != null
        frag.description.content == "My fragment description"
        !frag.description.multiLine
        frag.name == "MyFragment"
        frag.typeCondition.name == "User"
    }

    def "parse fragment definition with multiline description"() {
        given:
        def input = '''
            """
            A detailed
            fragment description
            """
            fragment Fields on Type {
                field1
            }
        '''

        when:
        Document document = Parser.parse(input)
        FragmentDefinition frag = document.definitions[0] as FragmentDefinition

        then:
        frag.description != null
        frag.description.multiLine
        frag.description.content.contains("A detailed")
        frag.description.content.contains("fragment description")
    }

    def "parse variable definition with description"() {
        given:
        def input = '''
            query MyQuery("variable description" $var: String) {
                foo(arg: $var)
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition
        VariableDefinition varDef = op.variableDefinitions[0]

        then:
        varDef.description != null
        varDef.description.content == "variable description"
        !varDef.description.multiLine
        varDef.name == "var"
        varDef.type.name == "String"
    }

    def "parse multiple variable definitions with descriptions"() {
        given:
        def input = '''
            query MyQuery(
                "First variable" $first: String,
                "Second variable" $second: Int
            ) {
                foo(a: $first, b: $second)
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition

        then:
        op.variableDefinitions.size() == 2
        op.variableDefinitions[0].description.content == "First variable"
        op.variableDefinitions[1].description.content == "Second variable"
    }

    def "operation without description still works"() {
        given:
        def input = '''
            query MyQuery {
                foo
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition

        then:
        op.description == null
    }

    def "fragment without description still works"() {
        given:
        def input = '''
            fragment MyFrag on User {
                name
            }
        '''

        when:
        Document document = Parser.parse(input)
        FragmentDefinition frag = document.definitions[0] as FragmentDefinition

        then:
        frag.description == null
    }

    def "variable without description still works"() {
        given:
        def input = '''
            query MyQuery($var: String) {
                foo(arg: $var)
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition

        then:
        op.variableDefinitions[0].description == null
    }

    def "AstPrinter prints operation description"() {
        given:
        def input = '''
            "My query description"
            query MyQuery {
                foo
            }
        '''

        when:
        Document document = Parser.parse(input)
        String printed = AstPrinter.printAst(document)

        then:
        printed.contains('"My query description"')
        printed.contains('query MyQuery')
    }

    def "AstPrinter prints fragment description"() {
        given:
        def input = '''
            "My fragment description"
            fragment MyFragment on User {
                name
            }
        '''

        when:
        Document document = Parser.parse(input)
        String printed = AstPrinter.printAst(document)

        then:
        printed.contains('"My fragment description"')
        printed.contains('fragment MyFragment on User')
    }

    def "AstPrinter prints variable description"() {
        given:
        def input = '''
            query MyQuery("variable desc" $var: String) {
                foo(arg: $var)
            }
        '''

        when:
        Document document = Parser.parse(input)
        String printed = AstPrinter.printAst(document)

        then:
        printed.contains('"variable desc"')
        printed.contains('$var: String')
    }

    def "description on anonymous query shortform is not used"() {
        given:
        def input = '''
            { foo }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition

        then:
        op.description == null
        op.operation == OperationDefinition.Operation.QUERY
    }

    def "combined document with descriptions on operations and fragments"() {
        given:
        def input = '''
            "Query description"
            query GetUser("user id" $id: ID!) {
                user(id: $id) {
                    ...UserFields
                }
            }

            "Fragment description"
            fragment UserFields on User {
                name
                email
            }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition
        FragmentDefinition frag = document.definitions[1] as FragmentDefinition

        then:
        op.description.content == "Query description"
        op.variableDefinitions[0].description.content == "user id"
        frag.description.content == "Fragment description"
    }

    def "deepCopy preserves description on OperationDefinition"() {
        given:
        def input = '"desc" query Q { foo }'

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition
        OperationDefinition copy = op.deepCopy()

        then:
        copy.description != null
        copy.description.content == "desc"
    }

    def "deepCopy preserves description on FragmentDefinition"() {
        given:
        def input = '"desc" fragment F on T { foo }'

        when:
        Document document = Parser.parse(input)
        FragmentDefinition frag = document.definitions[0] as FragmentDefinition
        FragmentDefinition copy = frag.deepCopy()

        then:
        copy.description != null
        copy.description.content == "desc"
    }

    def "deepCopy preserves description on VariableDefinition"() {
        given:
        def input = 'query Q("desc" $v: String) { foo }'

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition
        VariableDefinition varDef = op.variableDefinitions[0]
        VariableDefinition copy = varDef.deepCopy()

        then:
        copy.description != null
        copy.description.content == "desc"
    }

    def "transform preserves description on OperationDefinition"() {
        given:
        def input = '"desc" query Q { foo }'

        when:
        Document document = Parser.parse(input)
        OperationDefinition op = document.definitions[0] as OperationDefinition
        OperationDefinition transformed = op.transform({ b -> b.name("NewName") })

        then:
        transformed.description != null
        transformed.description.content == "desc"
        transformed.name == "NewName"
    }
}
