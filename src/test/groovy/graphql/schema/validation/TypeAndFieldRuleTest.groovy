package graphql.schema.validation

import graphql.AssertException
import graphql.TestUtil
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import spock.lang.Specification


import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class TypeAndFieldRuleTest extends Specification {


    def "type must define one or more fields."() {
        when:
        def sdl = '''
        type Query {}
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\n\"Query\" must define one or more fields."
    }

    def "Enum type must define one or more enum values"() {
        when:
        def sdl = '''
        type Query {
            enumValue: EnumType
        }
        enum EnumType {}
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\nEnum type \"EnumType\" must define one or more enum values."
    }

    def "the member types of a Union type must be unique"() {
        when:
        def sdl = '''
        type Query { field: Int }
        
        type A{ field: Int }
        
        union UnionType = A | A
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\nThe member types of a Union type must be unique. member type \"A\" in Union \"UnionType\" is not unique."
    }

    def "A non null type cannot wrap an existing non null type"() {
        given:
        def graphqlObjectType = GraphQLObjectType.newObject()
                .name("TypeA")
                .field(newFieldDefinition()
                        .name("field")
                        .type(GraphQLString))
                .build()

        when:
        def nestedNonNullType = GraphQLNonNull.nonNull(GraphQLNonNull.nonNull(graphqlObjectType))
        newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(newFieldDefinition()
                                .name("field")
                                .type(nestedNonNullType))
                        .build()
        ).build()

        then:
        def exception = thrown(AssertException)
        exception.message.contains("A non null type cannot wrap an existing non null type")
    }

    def "customized type name must not begin with \"__\""() {
        when:
        def sdl = '''
        type Query { field: Int }
        
        type __A{ field: Int }
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\n\"__A\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }

    def "field name must not begin with \"__\""() {
        when:
        def sdl = '''
        type Query { __namedField: Int }
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\n\"__namedField\" in \"Query\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }

    def "argument name must not begin with \"__\""() {
        when:
        def sdl = '''
        type Query { namedField(__arg: Int): Int }
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\nArgument name \"__arg\" in \"Query-namedField\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }
}
