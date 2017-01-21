package graphql

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
        def executionResult = GraphQL.newGraphQL(GarfieldSchema.GarfieldSchema).build().execute(query)

        then:
        executionResult.data == expectedResult


    }


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
        def executionResult = GraphQL.newGraphQL(GarfieldSchema.GarfieldSchema).build().execute(query, GarfieldSchema.john)

        then:
        executionResult.data == expectedResult

    }

    def "allows fragment conditions to be abstract types"() {
        given:
        def query = """
        {
            __typename
            name
            pets { ...PetFields }
            friends { ...FriendFields }
        }
         fragment PetFields on Pet {
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
        fragment FriendFields on Named {
            __typename
            name
            ... on Dog {
                barks
            }
            ... on Cat {
                meows
            }
        }
                """

        def expectedResult = [
                __typename: 'Person',
                name      : 'John',
                pets      : [
                        [__typename: 'Cat', name: 'Garfield', meows: false],
                        [__typename: 'Dog', name: 'Odie', barks: true]
                ],
                friends   : [
                        [__typename: 'Person', name: 'Liz'],
                        [__typename: 'Dog', name: 'Odie', barks: true]
                ]
        ]
        when:
        def executionResult = GraphQL.newGraphQL(GarfieldSchema.GarfieldSchema).build().execute(query, GarfieldSchema.john)

        then:
        executionResult.data == expectedResult
    }

}
