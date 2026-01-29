package graphql.validation

import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import org.codehaus.groovy.runtime.StringGroovyMethods
import spock.lang.Specification

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
        def query = """
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
        def query = StringGroovyMethods.stripIndent("""
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
            """)
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors[0].validationErrorType == ValidationErrorType.NonExecutableDefinition
        validationErrors[0].locations == [new SourceLocation(8, 1)]
        validationErrors[0].message == "Validation error (NonExecutableDefinition) : Type 'Cow' definition is not executable"
        validationErrors[1].validationErrorType == ValidationErrorType.NonExecutableDefinition
        validationErrors[1].locations == [new SourceLocation(12, 1)]
        validationErrors[1].message == "Validation error (NonExecutableDefinition) : Type 'Dog' definition is not executable"
    }

    def 'Executable Definitions with schema definition'() {
        def query = StringGroovyMethods.stripIndent("""
              schema {
                query: QueryRoot
              }
        
              type QueryRoot {
                test: String
              }
            """)
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors[0].validationErrorType == ValidationErrorType.NonExecutableDefinition
        validationErrors[0].locations == [new SourceLocation(2, 1)]
        validationErrors[0].message == "Validation error (NonExecutableDefinition) : Schema definition is not executable"
        validationErrors[1].validationErrorType == ValidationErrorType.NonExecutableDefinition
        validationErrors[1].locations == [new SourceLocation(6, 1)]
        validationErrors[1].message == "Validation error (NonExecutableDefinition) : Type 'QueryRoot' definition is not executable"
    }

    def 'Executable Definitions with input value type definition'() {
        def query = """
            type QueryRoot {               
                getDog(id: String!): String
            }
            """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.NonExecutableDefinition
        validationErrors[0].locations == [new SourceLocation(2, 1)]
        validationErrors[0].message == "Validation error (NonExecutableDefinition) : Type 'QueryRoot' definition is not executable"
    }

    def 'Executable Definitions with no directive definition'() {
        def query = StringGroovyMethods.stripIndent("""
              directive @nope on INPUT_OBJECT
            """)
        when:
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.NonExecutableDefinition
        validationErrors[0].locations == [new SourceLocation(2, 1)]
        validationErrors[0].message == "Validation error (NonExecutableDefinition) : Directive 'nope' definition is not executable"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(Harness.Schema, document, Locale.ENGLISH)
    }
}
