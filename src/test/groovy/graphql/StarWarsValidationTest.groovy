package graphql

import graphql.parser.Parser
import graphql.validation.ValidationError
import graphql.validation.Validator
import spock.lang.Ignore
import spock.lang.Specification


class StarWarsValidationTest extends Specification{


    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(StarWarsSchema.starWarsSchema, document)
    }

    @Ignore // not yet
    def 'Notes that non-existent fields are invalid'() {
        given:
        def query = """
        query HeroSpaceshipQuery {
            hero {
                favoriteSpaceship
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() > 0
    }
}
