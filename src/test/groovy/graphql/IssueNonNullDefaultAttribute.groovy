package graphql

import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

// See https://github.com/facebook/graphql/pull/418
class IssueNonNullDefaultAttribute extends Specification {
    def spec = '''
            input Locale {
                country: String! = "CA"
                language: String! = "en"
            }
            
            type Query {
                name(
                    characterNumber: Int! = 2
                    locale: Locale! = {country: "US" language: "en"}
                ): String
            }
            '''

    def nameFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment env) {
            def number = env.getArgument("characterNumber")
            def locale = env.getArgument("locale")

            def country = locale["country"]
            def language = locale["language"]
            return sprintf("Character No. $number $country-$language", number, country, language)
        }
    }

    def typeRuntimeWiring = newTypeWiring('Query').dataFetcher("name", nameFetcher).build()
    def runtimeWiring = newRuntimeWiring().type(typeRuntimeWiring).build()
    def graphql = TestUtil.graphQL(spec, runtimeWiring).build()

    def "Can omit non-null attributes that have default values"() {
        when:
        def result = graphql.execute('''
                {
                    name
                }
            ''')

        then:
        result.errors.isEmpty()
        result.data == [name: "Character No. 2 US-en"]
    }

    // Already works, should continue to work
    def "Explicit null value for non-null attribute causes validation error"() {
        when:
        def result = graphql.execute('''
                {
                    name(characterNumber: null)
                }
            ''')

        then:
        result.errors.size() == 1
        result.errors[0].errorType == ErrorType.ValidationError
        result.errors[0].message == "Validation error (WrongType@[name]) : argument 'characterNumber' with value 'NullValue{}' must not be null"
        result.errors[0].locations == [new SourceLocation(3, 26)]
        result.data == null

    }

    // Already works, should continue to work
    def "Provided non-null attribute will override default value"() {
        when:
        def result = graphql.execute('''
                {
                    name(characterNumber: 3)
                }
            ''')

        then:
        result.errors.isEmpty()
        result.data == [name: "Character No. 3 US-en"]
    }

    def "Can omit non-null attributes in input objects that have default values"() {
        when:
        def result = graphql.execute('''
                {
                    name(locale: {})
                }
            ''')

        then:
        result.errors.isEmpty()
        result.data == [name: "Character No. 2 CA-en"]
    }

    def "Provided non-null attribute in input objects will override default value"() {
        when:
        def result = graphql.execute('''
                {
                    name(locale: {language: "fr"})
                }
            ''')

        then:
        result.errors.isEmpty()
        result.data == [name: "Character No. 2 CA-fr"]
    }

}
