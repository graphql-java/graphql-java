package graphql.schema

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.UnExecutableSchemaGenerator
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.util.TreeTransformerUtil.changeNode

class AstDefinitionPreservationTest extends Specification {

    def "copies retain AST metadata for #original.class.simpleName"() {
        given:
        def copies = [
                original.transform { },
                original.copy(),
                copyBuilder(original).build(),
                original.withNewChildren(original.childrenWithTypeReferences)
        ]

        expect:
        original.definition != null
        copies.each { copied -> assertAstPreserved(original, copied) }

        where:
        [original, copyBuilder] << schemaElements()
    }

    def "definitions can be explicitly cleared for #original.class.simpleName"() {
        when:
        def cleared = original.transform { it.definition(null) }

        then:
        cleared.definition == null
        cleared.copy().definition == null
        original.definition != null

        where:
        [original, copyBuilder] << schemaElements()
    }

    def "renaming a runtime element retains the original source AST"() {
        given:
        def original = schema().queryType

        when:
        def renamed = original.transform { it.name('RenamedQuery') }

        then:
        renamed.name == 'RenamedQuery'
        renamed.definition.name == 'Query'
        assertAstPreserved(original, renamed)
    }

    def "schema copies retain their definition and extension ASTs - #operation"() {
        given:
        def original = schema()

        when:
        def copied = copySchema(original)

        then:
        !copied.is(original)
        original.definition != null
        original.extensionDefinitions.size() == 1
        copied.definition.is(original.definition)
        copied.extensionDefinitions[0].is(original.extensionDefinitions[0])

        where:
        operation               | copySchema
        'transform'             | { it.transform { } }
        'copy builder'          | { GraphQLSchema.newSchema(it).build() }
        'transformWithoutTypes' | { it.transformWithoutTypes { } }
    }

    def "schema AST metadata can be explicitly cleared"() {
        given:
        def original = schema()

        when:
        def cleared = original.transform { it.definition(null).extensionDefinitions([]) }

        then:
        cleared.definition == null
        cleared.extensionDefinitions.empty
        original.definition != null
        original.extensionDefinitions.size() == 1
    }

    def "schema transformation retains ASTs when rebuilding ancestors - with deletes #withDeletes"() {
        given:
        def original = schema()
        def visitor = [visitGraphQLAppliedDirectiveArgument: {
            GraphQLAppliedDirectiveArgument argument, TraverserContext<GraphQLSchemaElement> context ->
                changeNode(context, argument.transform { it.valueProgrammatic('changed') })
        }] as GraphQLTypeVisitorStub

        when:
        def transformed = withDeletes
                ? SchemaTransformer.transformSchemaWithDeletes(original, visitor)
                : SchemaTransformer.transformSchema(original, visitor)

        then:
        !transformed.is(original)
        transformed.definition.is(original.definition)
        transformed.extensionDefinitions[0].is(original.extensionDefinitions[0])
        assertAstPreserved(original.queryType, transformed.queryType)
        assertAstPreserved(original.getType('Color'), transformed.getType('Color'))
        assertAstPreserved(original.getType('Color').getValue('RED'), transformed.getType('Color').getValue('RED'))
        assertAppliedAstPreserved(original.queryType, transformed.queryType)
        assertAppliedAstPreserved(original.getType('Color').getValue('RED'), transformed.getType('Color').getValue('RED'))

        where:
        withDeletes << [false, true]
    }

    def "schema transformation without changes retains the original schema - with deletes #withDeletes"() {
        given:
        def original = schema()
        def visitor = new GraphQLTypeVisitorStub()

        expect:
        def transformed = withDeletes
                ? SchemaTransformer.transformSchemaWithDeletes(original, visitor)
                : SchemaTransformer.transformSchema(original, visitor)
        transformed.is(original)

        where:
        withDeletes << [false, true]
    }

    private static void assertAstPreserved(GraphQLNamedSchemaElement original, GraphQLNamedSchemaElement copied) {
        assert !copied.is(original)
        assert original.definition != null
        assert copied.definition.is(original.definition)
        if (original.metaClass.respondsTo(original, 'getExtensionDefinitions')) {
            assert original.extensionDefinitions.size() == 1
            assert copied.extensionDefinitions.size() == 1
            assert copied.extensionDefinitions[0].is(original.extensionDefinitions[0])
        }
    }

    private static void assertAppliedAstPreserved(GraphQLDirectiveContainer original, GraphQLDirectiveContainer copied) {
        def directive = original.getAppliedDirective('tag')
        def copiedDirective = copied.getAppliedDirective('tag')
        assertAstPreserved(directive, copiedDirective)
        assertAstPreserved(directive.getArgument('arg'), copiedDirective.getArgument('arg'))
        assert copiedDirective.getArgument('arg').value == 'changed'
    }

    private static List<List> schemaElements() {
        def original = schema()
        def field = original.queryType.getFieldDefinition('field')
        def applied = original.queryType.getAppliedDirective('tag')
        return [
                [original.queryType, { GraphQLObjectType.newObject(it) }],
                [original.getType('Node'), { GraphQLInterfaceType.newInterface(it) }],
                [original.getType('Choice'), { GraphQLUnionType.newUnionType(it) }],
                [original.getType('Color'), { GraphQLEnumType.newEnum(it) }],
                [original.getType('Custom'), { GraphQLScalarType.newScalar(it) }],
                [original.getType('Filter'), { GraphQLInputObjectType.newInputObject(it) }],
                [original.getDirective('tag'), { GraphQLDirective.newDirective(it) }],
                [field, { GraphQLFieldDefinition.newFieldDefinition(it) }],
                [field.getArgument('arg'), { GraphQLArgument.newArgument(it) }],
                [original.getType('Filter').getField('value'), { GraphQLInputObjectField.newInputObjectField(it) }],
                [original.getType('Color').getValue('RED'), { GraphQLEnumValueDefinition.newEnumValueDefinition(it) }],
                [applied, { GraphQLAppliedDirective.newDirective(it) }],
                [applied.getArgument('arg'), { GraphQLAppliedDirectiveArgument.newArgument(it) }]
        ]
    }

    private static GraphQLSchema schema() {
        def registry = new SchemaParser().parse('''
            directive @meta repeatable on DIRECTIVE_DEFINITION
            directive @tag(arg: String) @meta repeatable on SCHEMA | OBJECT | ENUM_VALUE
            extend directive @tag @meta
            schema @tag(arg: "schema") { query: Query }
            extend schema @tag(arg: "extension")
            type Query implements Node @tag(arg: "query") {
                id: ID!
                extra: String
                field(arg: Filter): Color
                choice: Choice
                custom: Custom
            }
            extend type Query { more: String }
            interface Node { id: ID! }
            extend interface Node { extra: String }
            type Other { other: String }
            union Choice = Query
            extend union Choice = Other
            scalar Custom
            extend scalar Custom @specifiedBy(url: "https://example.com/custom")
            enum Color { RED @tag(arg: "red") }
            extend enum Color { BLUE }
            input Filter { value: String }
            extend input Filter { other: String }
        ''')
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
    }
}
