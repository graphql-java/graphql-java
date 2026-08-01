package graphql.schema.universe

import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

class SUSchemaGeneratorTest extends Specification {

    def "compiles roots cyclic types and flattened extensions directly from SDL"() {
        given:
        def sdl = '''
            directive @tag(value: String = "default") repeatable on SCHEMA | OBJECT | FIELD_DEFINITION

            """The schema description."""
            schema @tag(value: "schema") {
              query: Root
              mutation: Changes
            }

            interface Node {
              id: ID!
            }

            type Root implements Node @tag {
              id: ID!
              next: Root
            }

            extend type Root {
              added: Choice
            }

            type Changes {
              change(input: Filter): Root
            }

            input Filter {
              nested: Filter
            }

            enum Choice {
              FIRST
            }

            extend enum Choice {
              SECOND
            }
        '''
        def universe = new SchemaUniverse()

        when:
        def schema = universe.parseSchema("native", sdl)

        then:
        schema.name == "native"
        schema.root.description == "The schema description."
        schema.queryType.name == "Root"
        schema.mutationType.name == "Changes"
        schema.subscriptionType == null
        schema.additionalTypes*.name.containsAll(["Node", "Filter", "Choice", "String", "ID", "Boolean"])

        and:
        def root = schema.getObjectType("Root")
        schema.getFields(root)*.name == ["id", "next", "added"]
        schema.getInterfaces(root)*.name == ["Node"]
        schema.getType(schema.getField(root, "next")).is(root)
        schema.getEnumValues(schema.getEnumType("Choice"))*.name == ["FIRST", "SECOND"]

        and:
        def filter = schema.getInputObjectType("Filter")
        schema.getType(schema.getInputField(filter, "nested")).is(filter)

        and:
        def schemaTag = schema.schemaAppliedDirectives[0]
        schemaTag.name == "tag"
        schemaTag.definition != null
        schemaTag.arguments*.name == ["value"]
        schemaTag.arguments[0].argumentValue.value instanceof StringValue
        schemaTag.arguments[0].argumentValue.value.value == "schema"

        and:
        def rootTag = schema.getAppliedDirectives(root)[0]
        rootTag.arguments*.name == ["value"]
        rootTag.arguments[0].argumentValue.value instanceof StringValue
        rootTag.arguments[0].argumentValue.value.value == "default"
    }

    def "uses conventional operation roots when schema definition is absent"() {
        given:
        def sdl = '''
            type Query {
              query: String
            }

            type Mutation {
              mutate: Boolean
            }

            type Subscription {
              changed: Int
            }
        '''

        when:
        def schema = new SchemaUniverse().parseSchema("defaults", sdl)

        then:
        schema.queryType.name == "Query"
        schema.mutationType.name == "Mutation"
        schema.subscriptionType.name == "Subscription"
        !schema.additionalTypes*.name.contains("Query")
        !schema.additionalTypes*.name.contains("Mutation")
        !schema.additionalTypes*.name.contains("Subscription")
    }

    def "preserves input defaults and all applied directive argument states"() {
        given:
        def sdl = '''
            directive @settings(
              explicit: String
              defaulted: Int = 42
              omitted: Boolean
              values: [String!] = ["a", "b"]
            ) on FIELD_DEFINITION

            input Filter {
              enabled: Boolean = true
              labels: [String!] = ["x"]
              nested: Nested = {active: false}
            }

            input Nested {
              active: Boolean!
            }

            type Query {
              value(
                filter: Filter = {enabled: false}
              ): String @settings(explicit: "yes")
            }
        '''

        when:
        def schema = new SchemaUniverse().importSchema(
                "defaults",
                new SchemaParser().parse(sdl))

        then:
        def query = schema.queryType
        def value = schema.getField(query, "value")
        def filterArgument = schema.getArgument(value, "filter")
        filterArgument.argumentDefaultValue.isLiteral()
        filterArgument.argumentDefaultValue.value instanceof ObjectValue

        and:
        def filter = schema.getInputObjectType("Filter")
        schema.getInputField(filter, "enabled").inputFieldDefaultValue.value instanceof BooleanValue
        schema.getInputField(filter, "labels").inputFieldDefaultValue.value instanceof ArrayValue
        schema.getInputField(filter, "nested").inputFieldDefaultValue.value instanceof ObjectValue

        and:
        def settings = schema.getAppliedDirectives(value)[0]
        settings.arguments*.name == ["explicit", "defaulted", "omitted", "values"]
        settings.getArgument("explicit").argumentValue.isLiteral()
        settings.getArgument("defaulted").argumentValue.isLiteral()
        settings.getArgument("omitted").argumentValue.isNotSet()
        settings.getArgument("values").argumentValue.isLiteral()

        and:
        def definition = schema.getDirectiveDefinition("settings")
        settings.arguments.each { appliedArgument ->
            def definitionArgument = schema.getArgument(definition, appliedArgument.name)
            schema.getType(appliedArgument).is(schema.getType(definitionArgument))
        }
    }

    def "creates fresh wrapper vertices for separate typed occurrences"() {
        given:
        def sdl = '''
            type Query {
              first(values: [String!]!): [String!]!
              second(values: [String!]!): [String!]!
            }
        '''

        when:
        def schema = new SchemaUniverse().parseSchema("wrappers", sdl)

        then:
        def first = schema.getField(schema.queryType, "first")
        def second = schema.getField(schema.queryType, "second")
        def firstArgument = schema.getArgument(first, "values")
        def secondArgument = schema.getArgument(second, "values")
        !schema.getType(first).is(schema.getType(second))
        !schema.getType(firstArgument).is(schema.getType(secondArgument))
        !schema.getType(first).is(schema.getType(firstArgument))
    }
}
