package graphql.execution

import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLObjectType
import spock.lang.Specification

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
        def result = fieldCollector.collectFields(fieldCollectorParameters, [field])

        then:
        result['bar1'] == [bar1]
        result['bar2'] == [bar2]
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
        def result = fieldCollector.collectFields(fieldCollectorParameters, [bar1Field])

        then:
        result['fieldOnInterface'] == [interfaceField]

    }
}
