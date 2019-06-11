package graphql.schema.idl

import graphql.GraphQL
import graphql.StarWarsData
import graphql.TestUtil
import graphql.schema.StaticDataFetcher
import spock.lang.Specification

import static TypeRuntimeWiring.newTypeWiring

/**
 This reruns some of the tests in graphql.StarWarsQueryTest but with the schema being
 built by via the schema generator
 */
class ExecutableStarWarsSchemaTest extends Specification {

    RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("QueryType")
            .dataFetchers(
            [
                    "hero" : new StaticDataFetcher(StarWarsData.getArtoo()),
                    "human": StarWarsData.getHumanDataFetcher(),
                    "droid": StarWarsData.getDroidDataFetcher()
            ])
    )
            .type(newTypeWiring("Human")
            .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
    )
            .type(newTypeWiring("Droid")
            .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
    )

            .type(newTypeWiring("Character")
            .typeResolver(StarWarsData.getCharacterTypeResolver())
    )
            .build()

    def executableStarWarsSchema = TestUtil.schemaFromResource("starWarsSchema.graphqls", wiring)


    def 'Correctly identifies R2-D2 as the hero of the Star Wars Saga'() {
        given:
        def query = """
        query HeroNameQuery {
          hero {
            name
          }
        }
        """
        def expected = [
                hero: [
                        name: 'R2-D2'
                ]
        ]

        when:
        def result = GraphQL.newGraphQL(executableStarWarsSchema).build().execute(query).data

        then:
        result == expected
    }

    def 'Allows us to query for the friends of friends of R2-D2'() {
        given:

        def query = """
        query NestedQuery {
            hero {
                name
                friends {
                    name
                    appearsIn
                    friends {
                        name
                    }
                }
            }
        }
        """
        def expected = [
                hero: [name   : 'R2-D2',
                       friends: [
                               [
                                       name     :
                                               'Luke Skywalker',
                                       appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                       friends  : [
                                               [
                                                       name: 'Han Solo',
                                               ],
                                               [
                                                       name: 'Leia Organa',
                                               ],
                                               [
                                                       name: 'C-3PO',
                                               ],
                                               [
                                                       name: 'R2-D2',
                                               ],
                                       ]
                               ],
                               [
                                       name     : 'Han Solo',
                                       appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                       friends  : [
                                               [
                                                       name: 'Luke Skywalker',
                                               ],
                                               [
                                                       name: 'Leia Organa',
                                               ],
                                               [
                                                       name: 'R2-D2',
                                               ],
                                       ]
                               ],
                               [
                                       name     : 'Leia Organa',
                                       appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                       friends  : [
                                               [
                                                       name: 'Luke Skywalker',
                                               ],
                                               [
                                                       name: 'Han Solo',
                                               ],
                                               [
                                                       name: 'C-3PO',
                                               ],
                                               [
                                                       name: 'R2-D2',
                                               ],
                                       ]
                               ],
                       ]
                ]
        ]

        when:
        def result = GraphQL.newGraphQL(executableStarWarsSchema).build().execute(query).data

        then:
        result == expected


    }

    def 'Allows us to query for both Luke and Leia, using two root fields and an alias'() {
        given:

        def query = """
        query FetchLukeAndLeiaAliased {
            luke:
            human(id: "1000") {
                name
            }
            leia:
            human(id: "1003") {
                name
            }
        }
        """
        def expected = [
                luke:
                        [
                                name: 'Luke Skywalker'
                        ],
                leia:
                        [
                                name: 'Leia Organa'
                        ]
        ]

        when:
        def result = GraphQL.newGraphQL(executableStarWarsSchema).build().execute(query).data

        then:
        result == expected

    }

    def 'Allows us to use a fragment to avoid duplicating content'() {
        given:
        def query = """
        query UseFragment {
            luke: human(id: "1000") {
                ...HumanFragment
            }
            leia: human(id: "1003") {
                ...HumanFragment
            }
        }
        fragment HumanFragment on Human {
            name
            homePlanet
        }
        """
        def expected = [
                luke: [
                        name      : 'Luke Skywalker',
                        homePlanet: 'Tatooine'
                ],
                leia: [
                        name      : 'Leia Organa',
                        homePlanet: 'Alderaan'
                ]
        ]
        when:
        def result = GraphQL.newGraphQL(executableStarWarsSchema).build().execute(query).data

        then:
        result == expected
    }

}
