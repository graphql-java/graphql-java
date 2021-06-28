package graphql.normalized

import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.language.Document
import graphql.language.VariableReference
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import graphql.validation.ValidationError
import spock.lang.Specification

import static graphql.language.AstPrinter.printAst
import static graphql.parser.Parser.parseValue

class PreNormalizedQueryFactoryTest extends Specification {

    def "pre-normalized query with variables and skip include"() {
        given:
        def schema = """
        type Query {
            pets: Pet
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
        }
        """

        String query = '''
      query($var1: Boolean!,$var2: Boolean!,$var3: Boolean!, $var4: Boolean!){
          pets {
                ... on Cat {
                    cat_not: name @skip(if:true)
                    cat_not: name @skip(if:$var1)
                    cat_yes_1: name @include(if:true)
                    cat_yes_2: name @skip(if:$var2)
              }
              ...@skip(if:$var3) @include(if:$var4) {
                ... on Dog @include(if:$var1) {
                    dog_no: name @include(if:false)
                    dog_no: name @include(if:$var1) @skip(if:$var2)  
                    dog_yes_1: name @include(if:$var1)
                    dog_yes_2: name @skip(if:$var2)
                }
              }
              ... on Pet @skip(if:$var1) {
                    not: name
              }
              ... on Pet @skip(if:$var2) {
                    pet_name: name
              }
          }}
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def preNormalizedQuery = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def printedTree = printTree(preNormalizedQuery)
        println String.join("\n", printedTree)
        then:
        printedTree == ['Query.pets (includeCondition:[[]])',
                        'cat_not: Cat.name (includeCondition:[[!var1]])',
                        'cat_yes_1: Cat.name (includeCondition:[[]])',
                        'cat_yes_2: Cat.name (includeCondition:[[!var2]])',
                        'dog_no: Dog.name (includeCondition:[[!var3, var4, var1, !var2]])',
                        'dog_yes_1: Dog.name (includeCondition:[[!var3, var4, var1]])',
                        'dog_yes_2: Dog.name (includeCondition:[[!var3, var4, var1, !var2]])',
                        'not: [Cat, Dog].name (includeCondition:[[!var1]])',
                        'pet_name: [Cat, Dog].name (includeCondition:[[!var2]])'
        ]
    }

    def "merged field with different skip include"() {
        given:
        def schema = """
        type Query {
            hello:String
        }
        """

        String query = '''
      query($var1: Boolean!,$var2: Boolean!,$var3: Boolean!, $var4: Boolean!){
        ...@skip(if:$var3) { 
          hello @skip(if:$var1) @include(if:$var2)
          ... @include(if:$var4) {
            hello    
          }
        }
        ...{ 
          hello @skip(if:$var3) @include(if:$var4)
        }
      }
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def preNormalizedQuery = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def tree = printTree(preNormalizedQuery)
        println String.join("\n", tree)
        then:
        true
    }

    def "merged field with and without skip include "() {
        given:
        def schema = """
        type Query {
            hello:String
        }
        """

        String query = '''
      query($var1: Boolean!,$var2: Boolean!,$var3: Boolean!, $var4: Boolean!){
        ...{ 
          hello @skip(if:$var1) @include(if:$var2)
        }
        ...{ 
          hello @skip(if:$var3) @include(if:$var4)
        }
        ... {
            hello
        }
      }
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def preNormalizedQuery = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def tree = printTree(preNormalizedQuery)
        println String.join("\n", tree)
        then:
        true
    }

    def "normalized arguments with lists"() {
        given:
        String schema = """
        type Query{ 
            search(arg1:[ID!], arg2:[[Input1]], arg3: [Input1]): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            query($var1: [Input1], $var2: ID!){
                search(arg1:["1",$var2], arg2: [[{foo: "foo1", input2: {bar: 123}},{foo: "foo2", input2: {bar: 456}}]], arg3: $var1) 
            }
        '''

        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def tree = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")
        def arg3 = topLevelField.getNormalizedArgument("arg3")

        then:
        arg1.typeName == "[ID!]"
        arg1.value.collect { printAst(it) } == ['"1"', '$var2']
        arg2.typeName == "[[Input1]]"
        arg2.value == [[
                               [foo: new NormalizedInputValue("String", parseValue('"foo1"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])],
                               [foo: new NormalizedInputValue("String", parseValue('"foo2"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("456"))])]
                       ]]

        arg3.getTypeName() == "[Input1]"
        arg3.value instanceof VariableReference
        (arg3.value as VariableReference).name == 'var1'


    }

    def "normalized arguments with lists 2"() {
        given:
        String schema = """
        type Query{ 
            search(arg1:[[Input1]] ,arg2:[[ID!]!]): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            query($var1: [Input1], $var2: [ID!]!){
                search(arg1: [$var1],arg2:[["1"],$var2] ) 
            }
        '''

        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def tree = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")

        then:
        arg1.typeName == "[[Input1]]"
        arg1.value instanceof List
        (arg1.value as List).size() == 1
        (arg1.value[0] as VariableReference).name == 'var1'

        arg2.typeName == "[[ID!]!]"
        arg2.value.collect { outer -> outer.collect { printAst(it) } } == [['"1"'], ['$var2']]
    }

    def "missing argument"() {
        given:
        String schema = """
        type Query {
            hello(arg: String): String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''{hello} '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def tree = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        println String.join("\n", printTree(tree))
        def printedTree = printTree(tree)


        then:
        printedTree == ['Query.hello (includeCondition:[[]])']
        tree.getTopLevelFields().get(0).getNormalizedArguments().isEmpty()
    }


    List<String> printTree(PreNormalizedQuery query) {
        def result = []
        Traverser<PreNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        traverser.traverse(query.getTopLevelFields(), new TraverserVisitorStub<PreNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<PreNormalizedField> context) {
                PreNormalizedField queryExecutionField = context.thisNode();
                result << queryExecutionField.printDetails()
                return TraversalControl.CONTINUE;
            }
        });
        result
    }

    private static void assertValidQuery(GraphQLSchema graphQLSchema, String query) {
        Parser parser = new Parser();
        Document document = parser.parseDocument(query);
        List<ValidationError> validationErrors = ParseAndValidate.validate(graphQLSchema, document);
        if (validationErrors.size() > 0) {
            println validationErrors
        }
        assert validationErrors.size() == 0
    }


}
