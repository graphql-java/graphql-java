package graphql

import graphql.language.SourceLocation
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class NullVariableCoercionTest extends Specification {

    def "null coercion errors have source locations"() {

        when:

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("bar",
                                { env ->
                                    Map<String, Object> map = new HashMap<>()
                                    map.put("id", "def")
                                    return map
                                })
                )
                .type(newTypeWiring("Node")
                        .typeResolver({ env -> (GraphQLObjectType) env.getSchema().getType("Foo") }))
                .build()


        def graphQL = TestUtil.graphQL("""
                schema {
                  query: Query
                }

                type Query {
                  bar(input: BarInput!): Node
                }

                input BarInput {
                  baz: String!
                }

                interface Node {
                  id: String
                }

                type Foo implements Node {
                  id: String
                }
            """, runtimeWiring).build()


        def variables = ["input": ["baz": null]]

        ExecutionInput varInput = newExecutionInput()
                .query('query Bar($input: BarInput!) {bar(input: $input) {id}}')
                .variables(variables)
                .build()

        ExecutionResult varResult = graphQL
                .executeAsync(varInput)
                .join()

        then:

        varResult.data == null
        varResult.errors.size() == 1
        varResult.errors[0].errorType == ErrorType.ValidationError
        varResult.errors[0].message == "Variable 'input' has an invalid value: Field 'baz' has coerced Null value for NonNull type 'String!'"
        varResult.errors[0].locations == [new SourceLocation(1, 11)]
    }

    def "can handle defaulting on complex input objects"() {
        def sdl = '''
            input Kitchen {
                pantry : String
            }
            
            input ListSnacksInput { 
              kitchen: [Kitchen!]! = [{pantry : "Cheezels"}] 
              snackType : String = "healthy"
              startPageToken: String 
            } 
            
            type Snacks {
                name : String
            }
            
            type Query {
              listSnacks(input: ListSnacksInput!) : [Snacks!]!
            }
        '''


        DataFetcher df = { env ->
            def val = env.getArgument("input")
            assert val instanceof Map
            def defaultedKitchenList = val["kitchen"]
            assert defaultedKitchenList instanceof List
            def snackVal = defaultedKitchenList[0]["pantry"]
            def snack = [name: snackVal]
            return [snack]
        }

        def graphQL = TestUtil.graphQL(sdl, [Query: [listSnacks: df]]).build()

        def query = '''
        query ($input: ListSnacksInput!){
          listSnacks(input: $input) {
             name
          }
        }
        '''

        when:
        def er = graphQL.execute(newExecutionInput(query).variables([input: [startPageToken: "dummyToken"]]))
        then:
        er.errors.isEmpty()
        er.data == [listSnacks: [[name: "Cheezels"]]]
    }
}

