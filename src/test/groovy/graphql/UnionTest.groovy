package graphql

import spock.lang.Ignore
import spock.lang.Specification

class UnionTest extends Specification {


    def "can introspect on union and intersection types"() {
        def query = """
            {
                Named: __type(name: "Named") {
                  kind
                  name
                  fields { name }
                  interfaces { name }
                  possibleTypes { name }
                  enumValues { name }
                  inputFields { name }
            }
                Pet: __type(name: "Pet") {
                  kind
                  name
                  fields { name }
                  interfaces { name }
                  possibleTypes { name }
                  enumValues { name }
                  inputFields { name }
                }
            }
            """

        def expectedResult = [Named: [
                kind         : 'INTERFACE',
                name         : 'Named',
                fields       : [
                        [name: 'name']
                ],
                interfaces   : null,
                possibleTypes: [
                        [name: 'Person'],
                        [name: 'Cat'],
                        [name: 'Dog']
                ],
                enumValues   : null,
                inputFields  : null
        ],
                              Pet  : [
                                      kind         : 'UNION',
                                      name         : 'Pet',
                                      fields       : null,
                                      interfaces   : null,
                                      possibleTypes: [
                                              [name: 'Cat'],
                                              [name: 'Dog']
                                      ],
                                      enumValues   : null,
                                      inputFields  : null
                              ]
        ]
        when:
        def executionResult = new GraphQL(GarfieldSchema.GarfieldSchema).execute(query)

        then:
        executionResult.result == expectedResult


    }


    @Ignore
    def "executes union types with inline fragments"() {

        def query = """
                {
                    __typename
                    name
                    pets {
                        __typename
                        ... on Dog {
                            name
                            barks
                        }
                        ... on Cat {
                            name
                            meows
                        }
                    }
                }
                """
        def expectedResult = [
                __typename: 'Person',
                name      : 'John',
                pets      : [
                        [__typename: 'Cat', name: 'Garfield', meows: false],
                        [__typename: 'Dog', name: 'Odie', barks: true]
                ]
        ]

        when:
        def executionResult = new GraphQL(GarfieldSchema.GarfieldSchema).execute(query)

        then:
        executionResult.result == expectedResult

    }

}
