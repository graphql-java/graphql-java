package graphql.validation

import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class NoUnusedFragmentsTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document, i18n)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.NO_UNUSED_FRAGMENTS })
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, operationValidator)
    }

    def "all fragment names are used"() {
        given:
        def query = """
                {
                    human(id: 4) {
                        ...HumanFields1
                        ... on Human {
                            ...HumanFields2
                        }
                    }
                }
                fragment HumanFields1 on Human {
                    name
                    ...HumanFields3
                }
                fragment HumanFields2 on Human {
                    name
                }
                fragment HumanFields3 on Human {
                    name
                }
                """

        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }

    def "all fragment names are used by multiple operations"() {
        given:
        def query = """
            query Foo {
                human(id: 4) {
                    ...HumanFields1
                }
            }
            query Bar {
                human(id: 4) {
                    ...HumanFields2
                }
            }
            fragment HumanFields1 on Human {
                name
                ...HumanFields3
            }
            fragment HumanFields2 on Human {
                name
            }
            fragment HumanFields3 on Human {
                name
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.getErrors().isEmpty()
    }


    def "contains unknown fragments"() {
        given:
        def query = """
                query Foo {
                    human(id: 4) {
                        ...HumanFields1
                    }
                }
                query Bar {
                    human(id: 4) {
                        ...HumanFields2
                    }
                }
                fragment HumanFields1 on Human {
                    name
                    ...HumanFields3
                }
                fragment HumanFields2 on Human {
                    name
                }
                fragment HumanFields3 on Human {
                    name
                }
                fragment Unused1 on Human {
                    name
                }
                fragment Unused2 on Human {
                    name
                }
                """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnusedFragment)
        errorCollector.getErrors().size() == 2

    }

    def "contains unknown fragments with ref cycle"() {
        given:
        def query = """
        query Foo {
            human(id: 4) {
                ...HumanFields1
            }
        }
        query Bar {
            human(id: 4) {
                ...HumanFields2
            }
        }
        fragment HumanFields1 on Human {
            name
            ...HumanFields3
        }
        fragment HumanFields2 on Human {
            name
        }
        fragment HumanFields3 on Human {
            name
        }
        fragment Unused1 on Human {
            name
            ...Unused2
        }
        fragment Unused2 on Human {
            name
            ...Unused1
        }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.UnusedFragment)
        errorCollector.getErrors().size() == 2
    }

    def "contains unused fragment with error message"() {
        def query = """
            query getDogName {
              dog {
                  name
              }
            }
            fragment dogFragment on Dog { barkVolume }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.UnusedFragment
        validationErrors[0].message == "Validation error (UnusedFragment) : Unused fragment 'dogFragment'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
