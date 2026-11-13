package graphql.language

import graphql.parser.Parser
import spock.lang.Specification

class DirectiveDefinitionDirectivesTest extends Specification {

    def "parses and prints directives on directive definitions and extensions"() {
        given:
        def document = new Parser().parseDocument('''
            directive @tag(value: String) repeatable on DIRECTIVE_DEFINITION
            directive @target(arg: String) @tag(value: "definition") repeatable on FIELD_DEFINITION
            extend directive @target @tag(value: "extension")
        ''')

        when:
        DirectiveDefinition definition = document.definitions[1]
        DirectiveExtensionDefinition extension = document.definitions[2]

        then:
        definition.directives*.name == ["tag"]
        definition.isRepeatable()
        extension.directives*.name == ["tag"]
        AstPrinter.printAstCompact(definition) ==
                'directive @target(arg:String) @tag(value:"definition") repeatable on FIELD_DEFINITION'
        AstPrinter.printAstCompact(extension) ==
                'extend directive @target @tag(value:"extension")'
    }

    def "deep copy transform and child replacement preserve directive order"() {
        given:
        def first = Directive.newDirective().name("first").build()
        def second = Directive.newDirective().name("second").build()
        def definition = DirectiveDefinition.newDirectiveDefinition()
                .name("target")
                .directive(first)
                .directive(second)
                .directiveLocation(DirectiveLocation.newDirectiveLocation().name("FIELD_DEFINITION").build())
                .build()

        expect:
        definition.deepCopy().directives*.name == ["first", "second"]
        definition.transform { it.directives([second, first]) }.directives*.name == ["second", "first"]

        when:
        def children = NodeChildrenContainer.newNodeChildrenContainer()
                .children(DirectiveDefinition.CHILD_INPUT_VALUE_DEFINITIONS, definition.inputValueDefinitions)
                .children(DirectiveDefinition.CHILD_DIRECTIVES, [second, first])
                .children(DirectiveDefinition.CHILD_DIRECTIVE_LOCATION, definition.directiveLocations)
                .build()

        then:
        definition.withNewChildren(children).directives*.name == ["second", "first"]
    }

    def "directive extension transforms without becoming a base definition"() {
        given:
        def extension = DirectiveExtensionDefinition.newDirectiveExtensionDefinition()
                .name("target")
                .directive(Directive.newDirective().name("first").build())
                .build()

        when:
        def transformed = extension.transformExtension {
            it.directive(Directive.newDirective().name("second").build())
        }

        then:
        transformed instanceof SDLExtensionDefinition
        transformed.directives*.name == ["first", "second"]
        transformed.deepCopy() instanceof DirectiveExtensionDefinition
    }

    def "pretty printer separates applied directives from arguments"() {
        expect:
        PrettyAstPrinter.print(
                'directive @target(arg: String) @tag repeatable on FIELD_DEFINITION',
                PrettyAstPrinter.PrettyPrinterOptions.defaultOptions
        ).trim() == 'directive @target(arg: String) @tag repeatable on FIELD_DEFINITION'

        and:
        PrettyAstPrinter.print(
                'extend directive @target @z @a',
                PrettyAstPrinter.PrettyPrinterOptions.defaultOptions
        ).trim() == 'extend directive @target @z @a'
    }

    def "AST sorter sorts directives on directive extensions"() {
        given:
        def document = new Parser().parseDocument('extend directive @target @z @a')

        when:
        def sorted = new AstSorter().sort(document)
        DirectiveExtensionDefinition extension = sorted.definitions[0]

        then:
        extension.directives*.name == ["a", "z"]
    }
}
