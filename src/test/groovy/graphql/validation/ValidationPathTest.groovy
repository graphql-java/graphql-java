package graphql.validation

class ValidationPathTest extends SpecValidationBase {

    def 'paths to validation errors are produced'() {
        def query = """
            query getDogName {
              dog {
                name
                doesKnowCommand # <-- missing arg here
                owner {
                  name(nonExistentArg : true)
                  badField
                }
              }
              elephant # <-- no such field
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 4
        validationErrors[0].queryPath == ["dog", "doesKnowCommand"]
        validationErrors[1].queryPath == ["dog", "owner", "name"]
        validationErrors[2].queryPath == ["dog", "owner", "badField"]
        validationErrors[3].queryPath == ["elephant"]
    }

    def "fragments validation errors have paths"() {
        def query = """
            query {
                dog {
                    ... namedFragment
                    
                    owner {
                        ... on Alien { # <-- invalid in this context
                            homePlanet(badArg:true)
                        }
                    }
                }
              }

            fragment namedFragment on Dog {
                name
                doesKnowCommand # <-- missing arg here
                owner {
                  name(nonExistentArg : true)
                  badField
                }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 5
        validationErrors[0].queryPath == ["dog", "owner"]
        validationErrors[1].queryPath == ["dog", "owner", "homePlanet"]
        validationErrors[2].queryPath == ["namedFragment", "doesKnowCommand"]
        validationErrors[3].queryPath == ["namedFragment", "owner", "name"]
        validationErrors[4].queryPath == ["namedFragment", "owner", "badField"]

    }
}
