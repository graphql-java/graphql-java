package graphql.validation.rules

import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.validation.rules.ExecutableDefinitions.nonExecutableDefinitionMessage

class ExecutableDefinitionsTest extends Specification {

    def 'Executable Definitions with only operation'() {
        def query = """\
              query Foo {
                dog {
                  name
                }
              }
            """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def 'Executable Definitions with operation and fragment'() {
        def query = """\
              query Foo {
                dog {
                  name
                  ...Frag
                }
              }
        
              fragment Frag on Dog {
                name
              }
            """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def 'Executable Definitions with type definition'() {
        def query = """\
              query Foo {
                dog {
                  name
                }
              }
        
              type Cow {
                name: String
              }
        
              extend type Dog {
                color: String
              }
            """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors[0] == nonExecutableDefinition("Cow", 7, 1)
        validationErrors[1] == nonExecutableDefinition("Dog", 11, 1)

    }

    def 'Executable Definitions with schema definition'() {
        def query = """\
              schema {
                query: QueryRoot
              }
        
              type QueryRoot {
                test: String
              }
            """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors[0] == nonExecutableDefinition("schema", 1, 1)
        validationErrors[1] == nonExecutableDefinition("QueryRoot", 5, 1)
    }

    def 'Executable Definitions with input value type definition'() {
        def query = """\
            type QueryRoot {               
                getDog(id: String!): String
            }
            """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0] == nonExecutableDefinition("QueryRoot", 1, 1)
    }


    ValidationError nonExecutableDefinition(String defName, int line, int column) {
        return new ValidationError(ValidationErrorType.NonExecutableDefinition,
                [new SourceLocation(line, column)],
                nonExecutableDefinitionMessage(defName))
    }

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(Harness.Schema, document)
    }
}
