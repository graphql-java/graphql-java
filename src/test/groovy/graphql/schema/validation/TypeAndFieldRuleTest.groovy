package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolverProxy
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType


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

    def "input type must define one or more fields"() {
        when:
        def sdl = '''
        type Query {
            field: String
        }
        input InputType {}
        
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        print(e.message)
        e.message == "invalid schema:\n\"InputType\" must define one or more fields."
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

    def "interface must define one or more fields."() {
        when:
        def sdl = '''
        type Query { field: Int }
        interface Interface {}
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\n\"Interface\" must define one or more fields."
    }

    def "interface name must not begin with \"__\""() {
        when:
        def sdl = '''
        type Query { field: Int }
        
        interface __A{ field: Int }
        '''

        TestUtil.schema(sdl)
        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\n\"__A\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }

    def "union name must not begin with \"__\""() {
        when:
        def sdl = '''
        type Query { field: Int }
        '''

        def objType1 = newObject().name("A")
                .field(newFieldDefinition().name("f1").type(GraphQLBoolean))
                .build()
        def objType2 = newObject().name("B")
                .field(newFieldDefinition().name("f1").type(GraphQLBoolean))
                .build()

        def unionType = newUnionType().name("__AB")
                .description("StartingDescription")
                .possibleType(objType1)
                .possibleType(objType2)
                .typeResolver(new TypeResolverProxy())
                .build()

        def graphQLSchema = TestUtil.schema(sdl)
        graphQLSchema.transform({ schema -> schema.additionalType(unionType) })

        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.message == "invalid schema:\n\"__AB\" must not begin with \"__\", which is reserved by GraphQL introspection."
    }
}
