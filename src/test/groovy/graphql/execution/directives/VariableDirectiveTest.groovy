package graphql.execution.directives

import graphql.TestUtil
import graphql.language.Directive
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.language.VariableDefinition
import graphql.validation.Validator
import spock.lang.Specification

class VariableDirectiveTest extends Specification {

    def sdl = '''
        directive @variableDirective(dirArg : String) on VARIABLE_DEFINITION
        
        directive @argumentDirective(dirArg : String) on ARGUMENT_DEFINITION
 
        type Query {
            f(fieldArg: String) : String
            f2: String
        }
    '''

    def schema = TestUtil.schema(sdl)


    def "valid variable directive"() {
        def spec = '''
            query Foo($arg: String @variableDirective(dirArg : "directive_arg_value")){
                f(fieldArg: $arg)
                f2
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator();
        def validationErrors = validator.validateDocument(schema, document);

        then:
        validationErrors.size() == 0
    }

    def "invalid variable directive position"() {

        def spec = '''
            query Foo($arg: String){
                f(fieldArg: $arg) @variableDirective(dirArg : "directive_arg_value")
                f2
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator();
        def validationErrors = validator.validateDocument(schema, document);

        then:
        validationErrors.size() == 1
        validationErrors[0].message == "Validation error of type MisplacedDirective: Directive variableDirective not allowed here @ 'f'"
    }

    def "invalid directive for variable"() {

        def spec = '''
            query Foo($arg: String @argumentDirective(dirArg : "directive_arg_value")){
                f(fieldArg: $arg) 
                f2
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)
        def validator = new Validator();
        def validationErrors = validator.validateDocument(schema, document);

        then:
        validationErrors.size() == 1
        validationErrors[0].message == "Validation error of type MisplacedDirective: Directive argumentDirective not allowed here"
    }


    def "get variable directive information from parsed Document"() {

        def spec = '''
            query Foo($arg: String @variableDirective(dirArg : "directive_arg_value")){
                f(fieldArg: $arg)
                f2
            }
        '''

        when:
        def document = TestUtil.parseQuery(spec)

        OperationDefinition operationDefinition = document.getDefinitions()[0]
        VariableDefinition variableDefinition = operationDefinition.getVariableDefinitions()[0]

        List<Directive> directives = variableDefinition.getDirectives()

        then:
        directives.size() == 1
        directives[0].getName() == "variableDirective"
        directives[0].getArguments().size() == 1
        ((StringValue) directives[0].getArgument("dirArg").getValue()).getValue() == "directive_arg_value"
    }

}
