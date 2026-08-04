package graphql.schema.idl

import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.language.DirectiveExtensionDefinition
import graphql.language.StringValue
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLDirective
import graphql.schema.idl.errors.DirectiveExtensionDirectiveRedefinitionError
import graphql.schema.idl.errors.DirectiveExtensionMissingBaseError
import graphql.schema.idl.errors.DirectiveIllegalLocationError
import graphql.schema.idl.errors.DirectiveIllegalReferenceError
import graphql.schema.idl.errors.DirectiveUndeclaredError
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification

class DirectivesOnDirectiveDefinitionsTest extends Specification {

    def "registry stores merges exposes and removes directive extensions"() {
        given:
        def baseRegistry = new SchemaParser().parse('''
            directive @meta repeatable on DIRECTIVE_DEFINITION
            directive @target on FIELD_DEFINITION
        ''')
        def extensionRegistry = new SchemaParser().parse('''
            extend directive @target @meta
            extend directive @target @meta
        ''')

        when:
        baseRegistry.merge(extensionRegistry)
        def extensions = baseRegistry.directiveExtensions()["target"]
        def secondExtension = extensions[1]

        then:
        extensions.size() == 2
        baseRegistry.readOnly().directiveExtensions()["target"] == extensions
        baseRegistry.parseOrder.inOrder[""].findAll { it instanceof DirectiveExtensionDefinition }.size() == 2

        when:
        baseRegistry.remove(extensions[0])

        then:
        baseRegistry.directiveExtensions()["target"].size() == 1

        when:
        baseRegistry.remove(secondExtension)
        baseRegistry.remove(secondExtension)

        then:
        !baseRegistry.directiveExtensions().containsKey("target")

        when:
        baseRegistry.add(secondExtension)
        baseRegistry.remove("target", secondExtension)

        then:
        !baseRegistry.directiveExtensions().containsKey("target")
    }

    def "schema generation combines definition and extension directives in order"() {
        given:
        def schema = TestUtil.schema('''
            directive @meta(value: String!) repeatable on DIRECTIVE_DEFINITION
            directive @target @meta(value: "definition") on FIELD_DEFINITION
            extend directive @target @meta(value: "first extension")
            extend directive @target @meta(value: "second extension")

            type Query {
                field: String
            }
        ''')

        when:
        def target = schema.getDirective("target")

        then:
        target.appliedDirectives*.name == ["meta", "meta", "meta"]
        target.getAppliedDirectives("meta")*.getArgument("value")*.argumentValue*.value*.value ==
                ["definition", "first extension", "second extension"]
        target.definition.directives*.name == ["meta"]
        target.extensionDefinitions.size() == 2

        and:
        new SchemaPrinter().print(target) ==
                'directive @target @meta(value : "definition") @meta(value : "first extension") @meta(value : "second extension") on FIELD_DEFINITION'
    }

    def "programmatic directive definitions are applied-directive containers and can be deprecated"() {
        given:
        def applied = GraphQLAppliedDirective.newDirective().name("meta").build()
        def extension = DirectiveExtensionDefinition.newDirectiveExtensionDefinition().name("target").build()
        def legacy = GraphQLDirective.newDirective()
                .name("legacy")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .build()
        def legacyBuilder = GraphQLDirective.newDirective()
                .name("legacyBuilder")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
        def appliedBuilder = GraphQLAppliedDirective.newDirective().name("metaBuilder")

        when:
        def directive = GraphQLDirective.newDirective()
                .name("target")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .withDirectives(legacy)
                .withDirective(legacyBuilder)
                .withAppliedDirectives(applied)
                .withAppliedDirective(appliedBuilder)
                .extensionDefinitions([extension])
                .deprecate("Use @replacement")
                .build()

        then:
        directive.hasAppliedDirective("meta")
        directive.getAppliedDirective("metaBuilder")
        directive.directivesByName.keySet() == ["legacy", "legacyBuilder"] as Set
        directive.getDirective("legacyBuilder")
        directive.isDeprecated()
        directive.deprecationReason == "Use @replacement"
        directive.extensionDefinitions == [extension]
        directive.copy().appliedDirectives*.name == ["meta", "metaBuilder"]
        new SchemaPrinter().print(directive).contains('@deprecated(reason : "Use @replacement")')
        directive.transform { it.clearDirectives() }.appliedDirectives.empty
    }

    def "deprecated is valid on directive definitions and uses the default reason"() {
        given:
        def schema = TestUtil.schema('''
            directive @old @deprecated on FIELD_DEFINITION
            type Query {
                field: String
            }
        ''')

        expect:
        schema.getDirective("old").deprecated
        schema.getDirective("old").deprecationReason == "No longer supported"
        graphql.Directives.DeprecatedDirective.validLocations()
                .contains(Introspection.DirectiveLocation.DIRECTIVE_DEFINITION)
    }

    def "repeatable directives may be applied across a definition and extensions"() {
        expect:
        TestUtil.schema('''
            directive @meta repeatable on DIRECTIVE_DEFINITION
            directive @target @meta on FIELD_DEFINITION
            extend directive @target @meta
            extend directive @target @meta
            type Query {
                field: String
            }
        ''').getDirective("target").getAppliedDirectives("meta").size() == 3
    }

    def "invalid directive definition applications are rejected - #name"() {
        when:
        schema(sdl)

        then:
        def problem = thrown(SchemaProblem)
        problem.errors.any { errorType.isInstance(it) }

        where:
        name                         | errorType                                     | sdl
        "unknown directive"          | DirectiveUndeclaredError                      | '''
            directive @target @unknown on FIELD_DEFINITION
            type Query { field: String }
        '''
        "illegal location"           | DirectiveIllegalLocationError                 | '''
            directive @meta on OBJECT
            directive @target @meta on FIELD_DEFINITION
            type Query { field: String }
        '''
        "extension without base"     | DirectiveExtensionMissingBaseError            | '''
            directive @meta on DIRECTIVE_DEFINITION
            extend directive @missing @meta
            type Query { field: String }
        '''
        "non-repeatable application" | DirectiveExtensionDirectiveRedefinitionError  | '''
            directive @meta on DIRECTIVE_DEFINITION
            directive @target @meta on FIELD_DEFINITION
            extend directive @target @meta
            type Query { field: String }
        '''
    }

    def "directive reference cycles are rejected - #name"() {
        when:
        schema(sdl)

        then:
        def problem = thrown(SchemaProblem)
        problem.errors.any { it instanceof DirectiveIllegalReferenceError }
        problem.errors.find { it instanceof DirectiveIllegalReferenceError }.message.contains(expectedPath)

        where:
        name                    | expectedPath        | sdl
        "direct self cycle"     | "'self'"            | '''
            directive @self @self on DIRECTIVE_DEFINITION
            type Query { field: String }
        '''
        "definition cycle"      | "a -> b -> a"       | '''
            directive @a @b on DIRECTIVE_DEFINITION
            directive @b @a on DIRECTIVE_DEFINITION
            type Query { field: String }
        '''
        "extension cycle"       | "a -> b -> a"       | '''
            directive @a on DIRECTIVE_DEFINITION
            directive @b on DIRECTIVE_DEFINITION
            extend directive @a @b
            extend directive @b @a
            type Query { field: String }
        '''
        "input type cycle"      | "loop -> LoopInput" | '''
            directive @loop(arg: LoopInput) on INPUT_FIELD_DEFINITION
            input LoopInput {
                field: String @loop
            }
            type Query { field: String }
        '''
        "type-led cycle"        | "b -> YInput -> XInput -> b" | '''
            directive @a(arg: XInput) on FIELD_DEFINITION
            directive @b(arg: YInput) on INPUT_OBJECT
            input XInput @b {
                field: String
            }
            input YInput {
                nested: XInput
            }
            type Query { field: String }
        '''
    }

    private static void schema(String sdl) {
        def registry = new SchemaParser().parse(sdl)
        def wiring = RuntimeWiring.newRuntimeWiring().build()
        new SchemaGenerator().makeExecutableSchema(registry, wiring)
    }
}
