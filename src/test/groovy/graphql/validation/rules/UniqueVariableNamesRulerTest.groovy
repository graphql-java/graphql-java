package graphql.validation.rules

import graphql.TestUtil
import graphql.parser.Parser
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class UniqueVariableNamesRulerTest extends Specification {

    def schema = TestUtil.schema('''
            type Query {
                field(arg: Int) : String
            }
        ''')

    def "normal query "() {

        def query = """
             query(\$arg:Int){
                field(arg: \$arg)
             } 
          """

        when:
        def document = Parser.parse(query)
        def validationResult = new Validator().validateDocument(schema, document)

        then:
        validationResult.size() == 0
    }

    def "duplicate variable name error"() {

        def query = """
             query(\$arg:Int,\$arg:Int){
                field(arg: \$arg)
             } 
          """

        when:
        def document = Parser.parse(query)
        def validationResult = new Validator().validateDocument(schema, document)

        then:
        validationResult.size() == 1
        (validationResult[0] as ValidationError).validationErrorType == ValidationErrorType.DuplicateVariableName
    }

}
