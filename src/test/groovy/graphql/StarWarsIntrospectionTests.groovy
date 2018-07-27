package graphql

import graphql.introspection.IntrospectionQuery
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
                                    [name: '__Directive'],
                                    [name: '__DirectiveLocation']]
                ]

        ]

        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query).data

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
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query).data

        then:
        result == expected
    }


    def "Allows querying the schema for a specific type"() {
        given:
        def query = """
        query IntrospectionDroidTypeQuery {
            __type(name: "Droid") {
                name
            }
        }
        """
        def expected = [
                __type: [
                        name: 'Droid'
                ]
        ]

        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query).data

        then:
        result == expected
    }

    def 'Allows querying the schema for an object kind'() {
        given:
        def query = """
        query IntrospectionDroidKindQuery {
            __type(name: "Droid") {
                name
                kind
            }
        }
        """
        def expected = [
                __type: [
                        name: 'Droid',
                        kind: 'OBJECT'
                ]
        ]
        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query).data

        then:
        result == expected
    }

    def 'Allows querying the schema for an interface kind'() {
        given:
        def query = """
        query IntrospectionCharacterKindQuery {
            __type(name: "Character") {
                name
                kind
            }
        }
        """
        def expected = [
                __type: [
                        name: 'Character',
                        kind: 'INTERFACE'
                ]
        ]
        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query).data

        then:
        result == expected
    }


    def 'Allows querying the schema for object fields'() {
        given:
        def query = """
        query IntrospectionDroidFieldsQuery {
            __type(name: "Droid") {
                name
                fields {
                    name
                    type {
                        name
                        kind
                    }
                }
            }
        }
        """
        def expected = [
                __type: [
                        name  : 'Droid',
                        fields: [
                                [
                                        name: 'id',
                                        type: [
                                                name: null,
                                                kind: 'NON_NULL'
                                        ]
                                ],
                                [
                                        name: 'name',
                                        type: [
                                                name: 'String',
                                                kind: 'SCALAR'
                                        ]
                                ],
                                [
                                        name: 'friends',
                                        type: [
                                                name: null,
                                                kind: 'LIST'
                                        ]
                                ],
                                [
                                        name: 'appearsIn',
                                        type: [
                                                name: null,
                                                kind: 'LIST'
                                        ]
                                ],
                                [
                                        name: 'primaryFunction',
                                        type: [
                                                name: 'String',
                                                kind: 'SCALAR'
                                        ]
                                ]
                        ]
                ]
        ]
        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query).data

        then:
        result == expected
    }

    def "Allows querying the schema for nested object fields"() {
        given:
        def query = """
        query IntrospectionDroidNestedFieldsQuery {
            __type(name: "Droid") {
                name
                fields {
                    name
                    type {
                        name
                        kind
                        ofType {
                            name
                            kind
                        }
                    }
                }
            }
        }
        """
        def expected = [
                __type: [
                        name  : 'Droid',
                        fields: [
                                [
                                        name: 'id',
                                        type: [
                                                name  : null,
                                                kind  : 'NON_NULL',
                                                ofType: [
                                                        name: 'String',
                                                        kind: 'SCALAR'
                                                ]
                                        ]
                                ],
                                [
                                        name: 'name',
                                        type: [
                                                name  : 'String',
                                                kind  : 'SCALAR',
                                                ofType: null
                                        ]
                                ],
                                [
                                        name: 'friends',
                                        type: [
                                                name  : null,
                                                kind  : 'LIST',
                                                ofType: [
                                                        name: 'Character',
                                                        kind: 'INTERFACE'
                                                ]
                                        ]
                                ],
                                [
                                        name: 'appearsIn',
                                        type: [
                                                name  : null,
                                                kind  : 'LIST',
                                                ofType: [
                                                        name: 'Episode',
                                                        kind: 'ENUM'
                                                ]
                                        ]
                                ],
                                [
                                        name: 'primaryFunction',
                                        type: [
                                                name  : 'String',
                                                kind  : 'SCALAR',
                                                ofType: null
                                        ]
                                ]
                        ]
                ]
        ]
        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query)

        then:
        result.data == expected
    }

    def 'Allows querying the schema for field args'() {
        given:
        def query = """
        query IntrospectionQueryTypeQuery {
            __schema {
                queryType {
                    fields {
                        name
                        args {
                            name
                            description
                            type {
                                name
                                kind
                                ofType {
                                    name
                                    kind
                                }
                            }
                            defaultValue
                        }
                    }
                }
            }
        }
        """
        def expected = [
                __schema: [
                        queryType: [
                                fields: [
                                        [
                                                name: 'hero',
                                                args: [
                                                        [

                                                                name        : 'episode',
                                                                description : 'If omitted, returns the hero of the whole ' +
                                                                        'saga. If provided, returns the hero of ' +
                                                                        'that particular episode.',
                                                                type        : [
                                                                        kind  : 'ENUM',
                                                                        name  : 'Episode',
                                                                        ofType: null
                                                                ],
                                                                defaultValue: null,

                                                        ]
                                                ]
                                        ],
                                        [
                                                name: 'human',
                                                args: [
                                                        [
                                                                name        : 'id',
                                                                description : 'id of the human',
                                                                type        : [
                                                                        kind  : 'NON_NULL',
                                                                        name  : null,
                                                                        ofType: [
                                                                                kind: 'SCALAR',
                                                                                name: 'String'
                                                                        ]
                                                                ],
                                                                defaultValue: null
                                                        ]
                                                ]
                                        ],
                                        [
                                                name: 'droid',
                                                args: [
                                                        [
                                                                name        : 'id',
                                                                description : 'id of the droid',
                                                                type        : [
                                                                        kind  : 'NON_NULL',
                                                                        name  : null,
                                                                        ofType: [
                                                                                kind: 'SCALAR',
                                                                                name: 'String'
                                                                        ]
                                                                ],
                                                                defaultValue: null
                                                        ]
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]

        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query)

        then:
        result.data == expected
    }

    def "Allows querying the schema for documentation"() {
        given:
        def query = """
        query IntrospectionDroidDescriptionQuery {
            __type(name: "Droid") {
                name
                description
            }
        }
        """
        def expected = [
                __type: [
                        name       : 'Droid',
                        description: 'A mechanical creature in the Star Wars universe.'
                ]
        ]

        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query)

        then:
        result.data == expected
    }

    def "Allow querying the schema with pre-defined full introspection query"() {
        given:
        def query = IntrospectionQuery.INTROSPECTION_QUERY

        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query)

        then:
        Map<String, Object> schema = (Map<String, Object>) result.data
        schema.size() == 1
        Map<String, Object> schemaParts = (Map<String, Map>) schema.get("__schema")
        schemaParts.size() == 5
        schemaParts.get('queryType').size() == 1
        schemaParts.get('mutationType') == null
        schemaParts.get('subscriptionType') == null
        schemaParts.get('types').size() == 15
        schemaParts.get('directives').size() == 3
    }
}
