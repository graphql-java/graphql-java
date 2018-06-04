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
            type Query {
                name(characterNumber: Int! = 2): String
            }
            '''

    def nameFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment env) {
            def number = env.getArgument("characterNumber")
            return "Character No. " + number
        }
    }

    def typeRuntimeWiring = newTypeWiring('Query').dataFetcher("name", nameFetcher).build()
    def runtimeWiring = newRuntimeWiring().type(typeRuntimeWiring).build()
    def qLSchema = TestUtil.schema(spec, runtimeWiring)
    def graphql = GraphQL.newGraphQL(qLSchema).build()

    def "Can omit non-null attributes that have default values"() {
        when:
        def result = graphql.execute('''
                {
                    name
                }
            ''')

        then:
        result.errors.isEmpty()
        result.data == [name: "Character No. 2"]
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
        result.errors[0].message == "Validation error of type WrongType: argument 'characterNumber' with value 'NullValue{}' must not be null @ 'name'"
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
        result.data == [name: "Character No. 3"]
    }

}
