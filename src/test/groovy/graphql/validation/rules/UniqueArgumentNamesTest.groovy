package graphql.validation.rules

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class UniqueArgumentNamesTest extends Specification {

    def "unique argument name"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") @dogDirective(arg1: "vlue"){
                  name @include(if: true)
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)

        then:
        validationErrors.empty
    }

    def "duplicate arguemnt name on field"() {
        def query = """
            query getDogName {
              dog(arg1:"value1",arg1:"value2") {
                  name
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateArgumentNames
    }

    def "duplicate arguemnt name on directive"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") {
                  name @include(if: true,if: false)
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateArgumentNames
    }

    def "duplicate arguemnt name on customed directive"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") @dogDirective(arg1: "vlue",arg1: "vlue2"){
                  name 
              }           
            }
        """
        when:
        def document = Parser.parse(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)


        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateArgumentNames
    }
}
