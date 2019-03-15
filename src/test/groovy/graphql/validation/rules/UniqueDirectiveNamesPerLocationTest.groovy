package graphql.validation.rules


import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class UniqueDirectiveNamesPerLocationTest extends Specification {

    def '5.7.3 Directives Are Unique Per Location - FragmentDefinition'() {
        def query = '''
        query getName {
            dog {
                name
                ... FragDef
                ... {
                  name
                }
            }
        }

        fragment FragDef on Dog @upper @lower @upper {
            name
        }
        '''.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        assertDuplicateDirectiveName("upper", "FragmentDefinition", 13, 39, validationErrors[0])
    }

    def '5.7.3 Directives Are Unique Per Location - OperationDefinition'() {
        def query = '''
        query getName @upper @lower @upper {
            dog {
                name
                ... FragDef
                ... {
                  name
                }
            }
        }

        fragment FragDef on Dog {
            name
        }
        '''.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        assertDuplicateDirectiveName("upper", "OperationDefinition", 3, 29, validationErrors[0])
    }

    def '5.7.3 Directives Are Unique Per Location - Field'() {
        def query = '''
        query getName {
            dog {
                name @upper @lower @upper
                ... FragDef
                ... {
                  name
                }
            }
        }

        fragment FragDef on Dog {
            name
        }
        '''.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        assertDuplicateDirectiveName("upper", "Field", 5, 28, validationErrors[0])
    }

    def '5.7.3 Directives Are Unique Per Location - FragmentSpread'() {
        def query = '''
        query getName {
            dog {
                name 
                ... FragDef @upper @lower @upper
                ... {
                  name
                }
            }
        }

        fragment FragDef on Dog {
            name
        }
        '''.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        assertDuplicateDirectiveName("upper", "FragmentSpread", 6, 35, validationErrors[0])
    }

    def '5.7.3 Directives Are Unique Per Location - InlineFragment'() {
        def query = '''
        query getName {
            dog {
                name 
                ... FragDef 
                ... @upper @lower @upper { 
                  name
                }
            }
        }

        fragment FragDef on Dog {
            name
        }
        '''.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        assertDuplicateDirectiveName("upper", "InlineFragment", 7, 27, validationErrors[0])
    }



    def assertDuplicateDirectiveName(String name, String type, int line, int column, ValidationError error) {
        assert error.locations[0].line == line
        assert error.locations[0].column == column
        assert error.validationErrorType == ValidationErrorType.DuplicateDirectiveName
        def expectedMsg = "Directives must be uniquely named within a location. The directive '${name}' used on a '${type}' is not unique."
        assert error.message.contains(expectedMsg)
        true
    }

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)
    }
}
