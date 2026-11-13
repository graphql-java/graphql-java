package graphql.validation


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
        validationErrors[0].locations[0].line == 12
        validationErrors[0].locations[0].column == 39
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateDirectiveName
        validationErrors[0].message == "Validation error (DuplicateDirectiveName@[FragDef]) : Non repeatable directives must be uniquely named within a location. The directive 'upper' used on a 'FragmentDefinition' is not unique"
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
        validationErrors[0].locations[0].line == 2
        validationErrors[0].locations[0].column == 29
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateDirectiveName
        validationErrors[0].message == "Validation error (DuplicateDirectiveName) : Non repeatable directives must be uniquely named within a location. The directive 'upper' used on a 'OperationDefinition' is not unique"
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
        validationErrors[0].locations[0].line == 4
        validationErrors[0].locations[0].column == 28
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateDirectiveName
        validationErrors[0].message == "Validation error (DuplicateDirectiveName@[dog/name]) : Non repeatable directives must be uniquely named within a location. The directive 'upper' used on a 'Field' is not unique"
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
        validationErrors[0].locations[0].line == 5
        validationErrors[0].locations[0].column == 35
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateDirectiveName
        validationErrors[0].message == "Validation error (DuplicateDirectiveName@[dog]) : Non repeatable directives must be uniquely named within a location. The directive 'upper' used on a 'FragmentSpread' is not unique"
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
        validationErrors[0].locations[0].line == 6
        validationErrors[0].locations[0].column == 27
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateDirectiveName
        validationErrors[0].message == "Validation error (DuplicateDirectiveName@[dog]) : Non repeatable directives must be uniquely named within a location. The directive 'upper' used on a 'InlineFragment' is not unique"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
