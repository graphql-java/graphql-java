package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import graphql.schema.TypeResolverProxy
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
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
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Interface", { null })
                .build()
        def graphQLSchema = TestUtil.schema(sdl).transform({it.codeRegistry(codeRegistry)})

        // this is a little convoluted, since this rule is repeated in the schemaChecker
        // we add the invalid union after schema creation so we can cover the validation from
        // the TypeAndFieldRule.
        def unionType = newUnionType().name("unionWithNonObjectTypes")
                .possibleType(graphQLSchema.getObjectType("Object"))
                .possibleType(GraphQLTypeReference.typeRef("Interface"))
                .build()
        codeRegistry = codeRegistry.transform({it.typeResolver(unionType, new TypeResolverProxy())})
        graphQLSchema.transform({ schema -> schema
                .additionalType(unionType)
                .codeRegistry(codeRegistry)})

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
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(unionType, new TypeResolverProxy())
                .build()

        graphQLSchema.transform({ schema -> schema
                .additionalType(unionType)
                .codeRegistry(codeRegistry) })

        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        !e.getErrors().isEmpty()
        e.getErrors()[0].classification == SchemaValidationErrorType.RepetitiveElementError
    }

    def "enum value name must not begin with __"() {
        given:
        def enumType = newEnum().name("EnumType").value("__reserved").value("OK").build()
        def query = newObject().name("Query").field(newFieldDefinition().name("e").type(enumType)).build()

        when:
        newSchema().query(query).build()

        then:
        InvalidSchemaException e = thrown(InvalidSchemaException)
        e.errors*.description == ['EnumValueDefinition "__reserved" in  "EnumType"  must not begin with "__", which is reserved by GraphQL introspection.']
    }

    def "union type must include one or more member types"() {
        given:
        def object = newObject().name("Object").field(newFieldDefinition().name("dummy").type(GraphQLString)).build()
        def union = newUnionType().name("EmptyUnion").possibleType(object).build()
        def query = newObject().name("Query").field(newFieldDefinition().name("u").type(union)).build()
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry().typeResolver("EmptyUnion", { null }).build()
        def schema = new graphql.schema.GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(codeRegistry), query, null, null)
                .addType(object)
                .addType(union)
                .build()
        // the union builder rejects empty member lists, so the members are cleared after the schema is built
        union.replaceTypes([])

        when:
        def errors = new SchemaValidator().validateSchema(schema)

        then:
        errors*.description == ['Union type "EmptyUnion" must include one or more unique member types.']
    }
}
