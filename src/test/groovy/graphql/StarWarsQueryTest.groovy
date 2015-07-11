package graphql

import spock.lang.Specification

class StarWarsQueryTest extends Specification {

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
        def result = new GraphQL(StarWarsSchema.starWarsSchema, query).execute()

        then:
        result == expected
    }


    def 'Allows us to query for the ID and friends of R2-D2'() {
        given:
        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
                name
                friends {
                    name
                }
            }
        }
        """
        def expected = [
                hero: [
                        id     : '2001',
                        name   : 'R2-D2',
                        friends: [
                                [
                                        name: 'Luke Skywalker',
                                ],
                                [
                                        name: 'Han Solo',
                                ],
                                [
                                        name: 'Leia Organa',
                                ],
                        ]
                ]
        ]

        when:
        def result = new GraphQL(StarWarsSchema.starWarsSchema, query).execute()

        then:
        result == expected
    }

}
