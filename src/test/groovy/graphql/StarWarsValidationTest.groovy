package graphql

import graphql.parser.Parser
import graphql.validation.ValidationError
import graphql.validation.Validator
import spock.lang.Specification

class StarWarsValidationTest extends Specification {


    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(StarWarsSchema.starWarsSchema, document)
    }

    def 'Validates a complex but valid query'() {
        def query = """
        query NestedQueryWithFragment {
            hero {
                ...NameAndAppearances
                friends {
                    ...NameAndAppearances
                    friends {
                        ...NameAndAppearances
                    }
                }
            }
        }

        fragment NameAndAppearances on Character {
            name
            appearsIn
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def 'Notes that non-existent fields are invalid'() {
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

    def 'Requires fields on objects'() {
        def query = """
        query HeroNoFieldsQuery {
            hero
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() > 0
    }

    def 'Disallows fields on scalars'() {
        def query = """
        query HeroFieldsOnScalarQuery {
            hero {
                name {
                    firstCharacterOfName
                }
            }
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() > 0
    }

    def 'Disallows object fields on interfaces'() {
        def query = """
        query DroidFieldOnCharacter {
            hero {
                name
                primaryFunction
            }
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() > 0
    }

    def 'Allows object fields in fragments'() {
        def query = """
        query DroidFieldInFragment {
            hero {
                name
                ...DroidFields
            }
        }

        fragment DroidFields on Droid {
            primaryFunction
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def 'Allows object fields in inline fragments'() {
        def query = """
        query DroidFieldInFragment {
            hero {
                name
                ... on Droid {
                    primaryFunction
                }
            }
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def 'Allows object fields in inline fragments on with no type'() {
        def query = """
        query InlineFragmentWithNoType {
            hero {
                name
                ... @include(if: true) {
                    appearsIn
                }
            }
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def 'Allows object fields in inline fragments with no type in array context'() {
        def query = """
        query InlineFragmentWithNoType {
            hero {
                name
                friends {
                    ... @include(if: true) {
                        name
                    }
                }
            }
        }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

}
