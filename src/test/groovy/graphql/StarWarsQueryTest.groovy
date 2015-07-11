package graphql

import spock.lang.Ignore
import spock.lang.Specification


class StarWarsQueryTest extends Specification {

    // not yet working
    @Ignore
    def 'Correctly identifies R2-D2 as the hero of the Star Wars Saga'() {
        given:
        def query = """
        query HeroNameQuery {
          hero {
            name
          }
        }
        """
        def expectedResult = [
                hero: [
                        name: 'R2-D2'
                ]
        ]

        when:
        def result = new GraphQL(StarWarsSchema.starWarsSchema, query).execute()

        then:
        result == expectedResult
    }


}
