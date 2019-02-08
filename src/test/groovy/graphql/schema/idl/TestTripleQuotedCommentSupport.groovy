package graphql.schema.idl


import graphql.TestUtil
import graphql.introspection.IntrospectionQuery
import spock.lang.Specification

class TestTripleQuotedCommentSupport extends Specification {

    def "1405 - triple quote support in Introspection"() {
        def sdl = '''
"""
A simple GraphQL schema which is well described.
And has multiple lines of description
"""
type Query {
  """
  Translates a string from a given language into a different language.
  """
  translate(
    "The original language that `text` is provided in."
    fromLanguage: Language

    "The translated language to be returned."
    toLanguage: Language

    "The text to be translated."
    text: String
  ): String
}

"""
The set of languages supported by `translate`.
"""
enum Language {
  "English"
  EN

  "French"
  FR

  "Chinese"
  CH
}
'''

        def graphQL = TestUtil.graphQL(sdl).build()

        when:
        def er = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        then:
        er.errors.isEmpty()
        def introspection = er.data
        def types = introspection["__schema"]["types"]

        def queryType = typeKindNamed(types, "OBJECT", "Query")
        queryType["description"] == """A simple GraphQL schema which is well described.\nAnd has multiple lines of description"""

        def translateField = named(queryType["fields"], "translate")
        translateField["description"] == " Translates a string from a given language into a different language.\n "

        def fromLanguage = named(translateField["args"], "fromLanguage")
        fromLanguage["description"] == "The original language that `text` is provided in."

        def languageEnum = typeKindNamed(types, "ENUM", "Language")
        languageEnum["description"] == "The set of languages supported by `translate`."
    }

    def typeKindNamed(types, String kind, String named) {
        types.find { it -> it["name"] == named && it["kind"] == kind.toUpperCase() }
    }

    def named(list, String named) {
        list.find { it -> it["name"] == named }
    }
}
