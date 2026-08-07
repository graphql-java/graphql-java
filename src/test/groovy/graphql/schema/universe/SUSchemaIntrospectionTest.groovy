package graphql.schema.universe

import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.introspection.IntrospectionWithDirectivesSupport
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphqlTypeComparatorRegistry
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

class SUSchemaIntrospectionTest extends Specification {

    def "every schema gets the canonical introspection graph by default"() {
        given:
        def universe = new SchemaUniverse()

        when:
        def schema = universe.parseSchema("schema", "type Query { value: String }")

        then:
        schema.introspectionSchemaType.is(schema.getObjectType("__Schema"))
        schema.introspectionSchemaType.name == Introspection.__Schema.name
        schema.getField(schema.queryType, "__schema") == null
        schema.getField(schema.queryType, "__type") == null

        and:
        standardIntrospectionNames().every { schema.getType(it) != null }
        schema.getFields(schema.introspectionSchemaType)*.name ==
                Introspection.__Schema.fieldDefinitions*.name

        and:
        def typesField = schema.getField(schema.introspectionSchemaType, "types")
        unwrap(schema, schema.getType(typesField)).is(schema.getObjectType("__Type"))
    }

    def "an explicit introspection root suppresses the canonical default"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def custom = universe.newObjectType("CustomIntrospection")

        when:
        def schema = universe.newSchema("schema")
                .queryType(query)
                .introspectionSchemaType(custom)
                .build()

        then:
        schema.introspectionSchemaType.is(custom)
        schema.getType("__Schema") == null
        schema.types == [custom, query]
    }

    def "default introspection reuses scalar types registered before build"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def string = universe.newScalarType("String")
        def booleanType = universe.newScalarType("Boolean")

        when:
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addType(string)
                .addType(booleanType)
                .build()

        then:
        schema.getScalarType("String").is(string)
        schema.getScalarType("Boolean").is(booleanType)
        schema.introspectionSchemaType.name == "__Schema"
    }

    def "schema transforms inherit and share their introspection topology"() {
        given:
        def universe = new SchemaUniverse()
        def base = universe.parseSchema("base", "type Query { value: String }")

        when:
        def transformed = base.transform("transformed", builder -> {
        })

        then:
        transformed.introspectionSchemaType.is(base.introspectionSchemaType)
        transformed.sharesOutgoingEdgesWith(
                base,
                base.introspectionSchemaType)
        transformed.getObjectType("__Type").is(base.getObjectType("__Type"))
    }

    def "custom GraphQLSchema introspection topology remains schema specific"() {
        given:
        GraphQLSchema graphQLSchema =
                TestUtil.schema("type Query { value: String }")
        GraphQLSchema enhancedGraphQLSchema =
                new IntrospectionWithDirectivesSupport(
                        environment -> true,
                        "_custom_")
                        .apply(graphQLSchema)
        def universe = new SchemaUniverse()

        when:
        def standard = universe.importSchema("standard", graphQLSchema)
        def enhanced =
                universe.importSchema("enhanced", enhancedGraphQLSchema)

        then:
        !standard.introspectionSchemaType.is(enhanced.introspectionSchemaType)
        standard.getObjectType("_custom_AppliedDirective") == null
        enhanced.getObjectType("_custom_AppliedDirective") != null
        enhanced.getObjectType("_custom_DirectiveArgument") != null

        and:
        ["__Schema", "__Type", "__Field", "__EnumValue", "__InputValue", "__Directive"]
                .every {
                    standard.getField(
                            standard.getObjectType(it),
                            "appliedDirectives") == null &&
                            enhanced.getField(
                                    enhanced.getObjectType(it),
                                    "appliedDirectives") != null
                }
        enhanced.getEnumType("__TypeKind") != null
        enhanced.getEnumType("__DirectiveLocation") != null

        when:
        def exported = enhanced.toGraphQLSchema()

        then:
        exported.introspectionSchemaType.getFieldDefinition("appliedDirectives") != null
        exported.getType("_custom_AppliedDirective") != null
        GraphQLTypeUtil.unwrapAll(
                exported.introspectionSchemaFieldDefinition.type)
                .is(exported.introspectionSchemaType)
        GraphQLTypeUtil.unwrapAll(
                exported.introspectionTypeFieldDefinition.type)
                .is(exported.getType("__Type"))
        exported.queryType.getFieldDefinition("__schema") == null
        exported.queryType.getFieldDefinition("__type") == null
    }

    def "default SU introspection topology prints identically to GraphQLSchema"() {
        given:
        def source = TestUtil.schema("type Query { value: String }")
        def exported = new SchemaUniverse()
                .parseSchema("schema", "type Query { value: String }")
                .toGraphQLSchema()
        def options = SchemaPrinter.Options.defaultOptions()
                .includeIntrospectionTypes(true)
                .includeSchemaDefinition(true)
                .setComparators(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY)
        def printer = new SchemaPrinter(options)

        expect:
        printer.print(exported) == printer.print(source)
    }

    private static Set<String> standardIntrospectionNames() {
        return [
                Introspection.__Schema,
                Introspection.__Type,
                Introspection.__Field,
                Introspection.__InputValue,
                Introspection.__EnumValue,
                Introspection.__Directive,
                Introspection.__TypeKind,
                Introspection.__DirectiveLocation
        ]*.name as Set
    }

    private static SUType unwrap(SUSchema schema, SUType type) {
        SUType current = type
        while (current instanceof SUListType || current instanceof SUNonNullType) {
            current = current instanceof SUListType
                    ? schema.getWrappedType((SUListType) current)
                    : schema.getWrappedType((SUNonNullType) current)
        }
        return current
    }
}
