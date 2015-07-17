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
        when:
        def exeutionResult = new GraphQL(GarfieldSchema.GarfieldSchema, query).execute()

        then:
        exeutionResult.result == [Named: [
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


    }

}
