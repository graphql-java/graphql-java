package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import graphql.schema.TypeResolverProxy
import spock.lang.Specification

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



    def "union member types must be object types"() {
        def sdl = '''
        type Query { dummy: String }
        
        type Object {
            dummy: String
        }
        
        interface Interface {
            dummy: String
        }
        '''
        when:
        def graphQLSchema = TestUtil.schema(sdl)

        // this is a little convoluted, since this rule is repeated in the schemaChecker
        // we add the invalid union after schema creation so we can cover the validation from
        // the TypeAndFieldRule.
        def unionType = newUnionType().name("unionWithNonObjectTypes")
                .possibleType(graphQLSchema.getObjectType("Object"))
                .possibleType(GraphQLTypeReference.typeRef("Interface"))
                .typeResolver(new TypeResolverProxy())
                .build()

        graphQLSchema.transform({ schema -> schema.additionalType(unionType) })

        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        !e.getErrors().isEmpty()
        e.getErrors()[0].classification == SchemaValidationErrorType.InvalidUnionMemberTypeError
    }

    def "union member types must be unique"() {
        def sdl = '''
        type Query { dummy: String }
        
        type Object {
            dummy: String
        }
        '''
        when:
        def graphQLSchema = TestUtil.schema(sdl)

        // Since this rule is repeated in the schemaChecker
        // there is no way to effectively cover it after the schema has
        // been constructed. We use a Stub here to register the same object type at two
        // different names.
        def stubObjectType = Stub(GraphQLObjectType) {
            getName() >>> ["Other","Object"]
        }

        def unionType = newUnionType().name("unionWithNonObjectTypes")
                .possibleType(graphQLSchema.getObjectType("Object"))
                .possibleType(stubObjectType)
                .typeResolver(new TypeResolverProxy())
                .build()

        graphQLSchema.transform({ schema -> schema.additionalType(unionType) })

        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        !e.getErrors().isEmpty()
        e.getErrors()[0].classification == SchemaValidationErrorType.RepetitiveElementError
    }
}
