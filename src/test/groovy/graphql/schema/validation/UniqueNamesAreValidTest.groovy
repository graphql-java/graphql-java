package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class UniqueNamesAreValidTest extends Specification {

    def "detects duplicate enum values via SDL"() {
        def sdl = '''
            type Query {
                field : MyEnum
            }
            enum MyEnum {
                A
                B
                A
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def e = thrown(InvalidSchemaException)
        e.getErrors().any { it.description.contains("MyEnum") && it.description.contains("duplicate") }
    }

    def "detects duplicate enum values via programmatic schema"() {
        when:
        def enumType = GraphQLEnumType.newEnum()
                .name("MyEnum")
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition().name("A").value("A").build())
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition().name("B").value("B").build())
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition().name("A").value("A2").build())
                .build()

        def schema = newSchema()
                .query(newObject()
                        .name("Query")
                        .field(newFieldDefinition().name("field").type(enumType).build())
                        .build())
                .build()

        then:
        def e = thrown(InvalidSchemaException)
        e.getErrors().any { it.description.contains("MyEnum") && it.description.contains("duplicate") }
    }

    def "no errors for valid schema"() {
        def sdl = '''
            type Query {
                field(arg1: String, arg2: Int) : String
            }
            enum MyEnum {
                A
                B
            }
        '''

        when:
        def schema = TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }
}
