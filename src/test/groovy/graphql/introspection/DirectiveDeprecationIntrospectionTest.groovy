package graphql.introspection

import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.DirectiveDefinition
import spock.lang.Specification

class DirectiveDeprecationIntrospectionTest extends Specification {

    def "introspection hides deprecated directives by default and exposes their metadata on request"() {
        given:
        def graphQL = TestUtil.graphQL('''
            directive @active on FIELD_DEFINITION
            directive @old @deprecated(reason: "Use @active") on FIELD_DEFINITION
            type Query {
                field: String
            }
        ''').build()

        when:
        def result = graphQL.execute('''
            {
                __schema {
                    defaultDirectives: directives {
                        name
                    }
                    allDirectives: directives(includeDeprecated: true) {
                        name
                        description
                        isDeprecated
                        deprecationReason
                        locations
                    }
                }
            }
        ''')

        then:
        result.errors.empty
        !result.data.__schema.defaultDirectives*.name.contains("old")
        result.data.__schema.defaultDirectives*.name.contains("active")

        and:
        def old = result.data.__schema.allDirectives.find { it.name == "old" }
        old.isDeprecated
        old.deprecationReason == "Use @active"
        def deprecated = result.data.__schema.allDirectives.find { it.name == "deprecated" }
        deprecated.description == "Marks an element of a GraphQL schema as no longer supported."
        deprecated.locations.contains("DIRECTIVE_DEFINITION")
    }

    def "the standard introspection query requests deprecated directives"() {
        expect:
        IntrospectionQuery.INTROSPECTION_QUERY.contains("directives(includeDeprecated: true)")
        IntrospectionQuery.INTROSPECTION_QUERY.contains("isDeprecated")
        IntrospectionQuery.INTROSPECTION_QUERY.contains("deprecationReason")

        and:
        !IntrospectionQueryBuilder.build(
                IntrospectionQueryBuilder.Options.defaultOptions().directiveDeprecation(false)
        ).contains("directives(includeDeprecated: true)")
    }

    def "introspection results recreate directive deprecation"() {
        given:
        def graphQL = TestUtil.graphQL('''
            directive @old @deprecated(reason: "Use something else") on FIELD_DEFINITION
            type Query {
                field: String
            }
        ''').build()
        def result = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        when:
        def document = new IntrospectionResultToSchema().createSchemaDefinition(result)
        DirectiveDefinition old = document.definitions.find {
            it instanceof DirectiveDefinition && it.name == "old"
        }

        then:
        old.directives*.name == ["deprecated"]
        AstPrinter.printAstCompact(old).contains('@deprecated(reason:"Use something else")')
    }
}
