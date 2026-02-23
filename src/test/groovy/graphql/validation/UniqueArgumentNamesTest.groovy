package graphql.validation

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class UniqueArgumentNamesTest extends Specification {

    def "unique argument name"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") @dogDirective(arg1: "value"){
                  name @include(if: true)
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)

        then:
        validationErrors.empty
    }

    def "duplicate argument name on field"() {
        def query = """
            query getDogName {
              dog(arg1:"value1",arg1:"value2") {
                  name
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateArgumentNames
        validationErrors.get(0).message == "Validation error (DuplicateArgumentNames@[dog]) : There can be only one argument named 'arg1'"
    }

    def "duplicate argument name on directive"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") {
                  name @include(if: true,if: false)
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateArgumentNames
        validationErrors.get(0).message == "Validation error (DuplicateArgumentNames@[dog/name]) : There can be only one argument named 'if'"
    }

    def "duplicate argument name on custom directive"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") @dogDirective(arg1: "value",arg1: "value2"){
                  name 
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)


        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateArgumentNames
        validationErrors.get(0).message == "Validation error (DuplicateArgumentNames@[dog]) : There can be only one argument named 'arg1'"
    }
}
