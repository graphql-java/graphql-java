package graphql

import spock.lang.Specification


class StarWarsIntrospectionTests extends Specification {

    def "Allows querying the schema for types"() {
        given:
        def query = """
        query IntrospectionTypeQuery {
            __schema {
                types {
                    name
                }
            }
        }
        """
        def expected = [
                __schema: [types:
                                   [[name: 'QueryType'],
                                    [name: 'Character'],
                                    [name: 'String'],
                                    [name: 'Episode'],
                                    [name: 'Human'],
                                    [name: 'Droid'],
                                    [name: '__Schema'],
                                    [name: '__Type'],
                                    [name: '__TypeKind'],
                                    [name: '__Field'],
                                    [name: '__InputValue'],
                                    [name: 'Boolean'],
                                    [name: '__EnumValue'],
                                    [name: '__Directive']]
                ]

        ];

        when:
        def result = new GraphQL(StarWarsSchema.starWarsSchema).execute(query).result

        then:
        result == expected
    }


    def "Allows querying the schema for query type"() {
        given:
        def query = """
        query IntrospectionQueryTypeQuery {
            __schema {
                queryType {
                    name
                }
            }
        }
        """
        def expected = [
                __schema: [
                        queryType: [
                                name: 'QueryType'
                        ],
                ]
        ]
        when:
        def result = new GraphQL(StarWarsSchema.starWarsSchema).execute(query).result

        then:
        result == expected
    }

}
