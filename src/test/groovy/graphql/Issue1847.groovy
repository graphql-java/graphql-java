package graphql

import spock.lang.Specification

class Issue1847 extends Specification {
    def schema = TestUtil.schema('''
                type Query {
                    listWithCircularReference(filter : TypeWithCircularReference) : String 
                    list(filter : Type) : String 
                }
                input TypeWithCircularReference {
                    circularField : TypeWithCircularReference
                }
                input Type {
                    field: String
                }
            ''')

    def "#1847 when there is a circular reference between Input Type and Input Field - not StackOverflowError"() {
        when:
        def type = schema.getType("TypeWithCircularReference")

        def string = type.toString()

        println(string)

        then:
        string.contains("[GraphQLInputObjectType]")
    }

    def "#1847 when there is no circular reference between Input Type and Input Field - toString returns all data"() {
        when:
        def type = schema.getType("Type")

        def string = type.toString()
        println(string)

        then:
        !string.contains("[GraphQLInputObjectType]")
        string.contains("GraphQLScalarType")
    }
}
