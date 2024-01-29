package graphql.execution

import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLObjectType
import spock.lang.Specification

import static graphql.TestUtil.mergedField
import static graphql.execution.FieldCollectorParameters.newParameters

class FieldCollectorTest extends Specification {


    def "collect fields"() {
        given:
        def schema = TestUtil.schema("""
            type Query {
                bar1: String
                bar2: String 
            }
                """)
        def objectType = schema.getType("Query") as GraphQLObjectType
        FieldCollector fieldCollector = new FieldCollector()
        FieldCollectorParameters fieldCollectorParameters = newParameters()
                .schema(schema)
                .objectType(objectType)
                .build()
        Document document = new Parser().parseDocument("{foo {bar1 bar2 }}")
        Field field = ((OperationDefinition) document.children[0]).selectionSet.selections[0] as Field

        def bar1 = field.selectionSet.selections[0]
        def bar2 = field.selectionSet.selections[1]

        when:
        def result = fieldCollector.collectFields(fieldCollectorParameters, mergedField(field))

        then:
        result.getSubField('bar1').getFields() == [bar1]
        result.getSubField('bar2').getFields() == [bar2]
    }

    def "collect fields on inline fragments"() {
        def schema = TestUtil.schema("""
            type Query{
                bar1: String
                bar2: Test 
            }
            interface Test {
                fieldOnInterface: String
            }
            type TestImpl implements Test {
                fieldOnInterface: String
            }
                """)
        def object = schema.getType("TestImpl") as GraphQLObjectType
        FieldCollector fieldCollector = new FieldCollector()
        FieldCollectorParameters fieldCollectorParameters = newParameters()
                .schema(schema)
                .objectType(object)
                .build()
        Document document = new Parser().parseDocument("{bar1 { ...on Test {fieldOnInterface}}}")
        Field bar1Field = ((OperationDefinition) document.children[0]).selectionSet.selections[0] as Field

        def inlineFragment = bar1Field.selectionSet.selections[0] as InlineFragment
        def interfaceField = inlineFragment.selectionSet.selections[0]

        when:
        def result = fieldCollector.collectFields(fieldCollectorParameters, mergedField(bar1Field))

        then:
        result.getSubField('fieldOnInterface').getFields() == [interfaceField]
    }

    def "collect fields that are merged together - one of the fields is on an inline fragment "() {
        def schema = TestUtil.schema("""
            type Query {
                echo: String
            }
""")

        Document document = new Parser().parseDocument("""
        {
            echo 
            ... on Query {
                echo
            }
        }
        
""")

        def object = schema.getType("TestImpl") as GraphQLObjectType
        FieldCollector fieldCollector = new FieldCollector()
        FieldCollectorParameters fieldCollectorParameters = newParameters()
                .schema(schema)
                .objectType(object)
                .build()

        def selectionSet = ((OperationDefinition) document.children[0]).selectionSet

        when:
        def result = fieldCollector.collectFields(fieldCollectorParameters, selectionSet)

        then:
        result.size() == 1
        result.getSubField('echo').fields.size() == 1
    }

    def "collect fields that are merged together - fields have different selection sets "() {
        def schema = TestUtil.schema("""
            type Query {
                me: Me
            }
            
            type Me {
                firstname: String
                lastname: String 
            }
""")

        Document document = new Parser().parseDocument("""
        {
            me {
                firstname
            } 
            me {
                lastname
            } 
        }
        
""")

        def object = schema.getType("TestImpl") as GraphQLObjectType
        FieldCollector fieldCollector = new FieldCollector()
        FieldCollectorParameters fieldCollectorParameters = newParameters()
                .schema(schema)
                .objectType(object)
                .build()

        def selectionSet = ((OperationDefinition) document.children[0]).selectionSet

        when:
        def result = fieldCollector.collectFields(fieldCollectorParameters, selectionSet)

        then:
        result.size() == 1

        def meField = result.getSubField('me')

        meField.fields.size() == 2

        meField.fields[0].selectionSet.selections.size() == 1
        meField.fields[0].selectionSet.selections[0].name == "firstname"

        meField.fields[1].selectionSet.selections.size() == 1
        meField.fields[1].selectionSet.selections[0].name == "lastname"
    }

    def "collect fields that are merged together - fields have different directives"() {
        def schema = TestUtil.schema("""
            directive @one on FIELD
            directive @two on FIELD
            
            type Query {
                echo: String 
            }
""")

        Document document = new Parser().parseDocument("""
        {
            echo @one
            echo @two
        }
        
""")

        def object = schema.getType("TestImpl") as GraphQLObjectType
        FieldCollector fieldCollector = new FieldCollector()
        FieldCollectorParameters fieldCollectorParameters = newParameters()
                .schema(schema)
                .objectType(object)
                .build()

        def selectionSet = ((OperationDefinition) document.children[0]).selectionSet

        when:
        def result = fieldCollector.collectFields(fieldCollectorParameters, selectionSet)

        then:
        result.size() == 1

        def echoField = result.getSubField('echo')

        echoField.fields.size() == 2

        echoField.fields[0].name == "echo"
        echoField.fields[0].directives.size() == 1
        echoField.fields[0].directives[0].name == "one"


        echoField.fields[1].name == "echo"
        echoField.fields[1].directives.size() == 1
        echoField.fields[1].directives[0].name == "two"
    }
}
