package graphql.execution.directives

import graphql.TestUtil
import graphql.language.Directive
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.validation.Validator
import spock.lang.Specification

class RepeatableDirectivesTest extends Specification {

    def sdl = '''
        directive @repeatableDirective(arg: String) repeatable on FIELD
         
        directive @nonRepeatableDirective on FIELD
        type Query {
           namedField: String
        }
    '''

    def schema = TestUtil.schema(sdl)


    def "repeatableDirectives"() {
        def spec = '''
            query {
                f1: namedField @repeatableDirective @repeatableDirective
                f2: namedField @repeatableDirective
                f3: namedField @nonRepeatableDirective
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document)

        then:
        validationErrors.size() == 0
    }

    def "nonRepeatableDirective"() {

        def spec = '''
            query {
                namedField @nonRepeatableDirective @nonRepeatableDirective
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document)

        then:
        validationErrors.size() == 1
        validationErrors[0].message == "Validation error of type DuplicateDirectiveName: Non repeatable directives must be uniquely named within a location. The directive 'nonRepeatableDirective' used on a 'Field' is not unique. @ 'namedField'"
    }

    def "getRepeatableDirectivesInfo"() {

        def spec = '''
            query {
                namedField @repeatableDirective(arg: "value1") @repeatableDirective(arg: "value2")
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator()
        def validationErrors = validator.validateDocument(schema, document)

        OperationDefinition operationDefinition = document.getDefinitions()[0]
        Field field = operationDefinition.getSelectionSet().getSelections()[0]
        List<Directive> directives = field.getDirectives()

        then:
        validationErrors.size() == 0
        directives.size() == 2
        ((StringValue) directives[0].getArgument("arg").getValue()).getValue() == "value1"
        ((StringValue) directives[1].getArgument("arg").getValue()).getValue() == "value2"
    }


}