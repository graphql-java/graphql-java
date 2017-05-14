package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaCompiler
import graphql.schema.idl.SchemaGenerator
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

/**
 * The testing of an implementation of https://github.com/facebook/graphql/pull/83
 */
class NullValueSupportTest extends Specification {
    GraphQLSchema generate(String s, RuntimeWiring wiring) {
        def registry = new SchemaCompiler().compile(s)
        new SchemaGenerator().makeExecutableSchema(registry, wiring)
    }

    def execute(String query, Map<String, Object> variables, DataFetcher df) {
        def wiring = newRuntimeWiring()
                .type(newTypeWiring("Mutation").dataFetcher("editThing", df))
                .build()
        GraphQLSchema schema = generate(schemaSpec, wiring)
        GraphQL graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(query, (Object) null, variables)
        if (!result.errors.isEmpty()) {
            throw new RuntimeException(String.valueOf(result.errors))
        }
        result
    }

    class AssertingArgDataFetcher implements DataFetcher {
        String msg
        Map<String, Object> expectedArgs

        AssertingArgDataFetcher(Map<String, Object> expectedArgs, String msg = "") {
            this.expectedArgs = expectedArgs
            this.msg = msg
        }

        @Override
        Object get(DataFetchingEnvironment environment) {
            def actualArgs = environment.getArguments()
            assert expectedArgs == actualArgs : msg
            return [name: "ThingObjName"]
        }
    }


    def schemaSpec = """
            schema {
               query: Query
               mutation: Mutation
            }
           
            type Query {
                thing : ThingObj
            }
            
            type ThingObj {
                name : String
            }
            
            input EditObj {
                foo : String
                bar : String
                baz : String
            }
            
            type Mutation {
                editThing(id : Int, edits : EditObj) : ThingObj
            }  
        """

    def "literal argument null specified values are handled"() {

        def df = new AssertingArgDataFetcher([id: 4, edits: [foo: "added", bar: null]])

        Map<String, Object> variables = [:]

        def query = '''
            mutation editThing {
              editThing(id: 4, edits: { foo: "added", bar: null }) {
                name
              }
            }
        '''

        when:
        def result = execute(query, variables, df)


        then:
        result.data != null
    }

    def "literal argument null not specified values are handled"() {

        def df = new AssertingArgDataFetcher([id: 4, edits: [foo: "added"]])


        Map<String, Object> variables = [:]

        def query = '''
            mutation editThing {
              editThing(id: 4, edits: { foo: "added"}) {
                name
              }
            }
        '''


        when:
        def result = execute(query, variables, df)

        then:
        result.data != null
    }

    def "variable argument null specified values are handled"() {

        def df = new AssertingArgDataFetcher([id: 4, edits: [foo: "added", bar: null]])

        Map<String, Object> variables = [edits: [foo: "added", bar: null]]

        def query = '''
            mutation editThing($edits: EditObj) {
              editThing(id: 4, edits: $edits) {
                name
              }
            }
        '''


        when:
        def result = execute(query, variables, df)

        then:
        result.data != null
    }

    def "variable argument null NOT specified values are handled"() {

        def df = new AssertingArgDataFetcher([id: 4, edits: [foo: "added"]])

        Map<String, Object> variables = [edits: [foo: "added"]]

        def query = '''
            mutation editThing($edits: EditObj) {
              editThing(id: 4, edits: $edits) {
                name
              }
            }
        '''


        when:
        def result = execute(query, variables, df)

        then:
        result.data == [editThing: [name: "ThingObjName"]]
    }

    def "positional arguments values are handled"() {

        def df1 = new AssertingArgDataFetcher([id: 4, edits: [foo: "added", bar: null]]
                , 'The "baz" input field is "not provided"')
        def df2 = new AssertingArgDataFetcher([id: 4, edits: [foo: "added", bar: null, baz: null]],
                'The "baz" input field is null')
        def df3 = new AssertingArgDataFetcher([id: 4, edits: [foo: "added", bar: null, baz: "added"]],
                'The "baz" input field is "added"')

        Map<String, Object> variables1 = [:]
        Map<String, Object> variables2 = ["editBaz": null]
        Map<String, Object> variables3 = ["editBaz": "added"]

        def query = '''
           mutation editThing($editBaz: String) {
                editThing(id: 4, edits: { foo: "added", bar: null, baz: $editBaz }) {
                    name
                }
            }
        '''


        when:

        def result1 = execute(query, variables1, df1)
        def result2 = execute(query, variables2, df2)
        def result3 = execute(query, variables3, df3)

        then:
        result1.data != null
        result2.data != null
        result3.data != null
    }

}
