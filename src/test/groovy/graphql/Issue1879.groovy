package graphql

import graphql.validation.Validator
import spock.lang.Specification
import graphql.parser.Parser;


class Issue1879 extends Specification {

    def schema = TestUtil.schema('''
            type Query {
                field(arg: [Int]) : String
            }
        ''')


    def "normal query "() {

        def query = """
             query{
                field(arg: [1,2,3])
             } 
          """

        when:
        def document = Parser.parse(query)
        def validationResult = new Validator().validateDocument(schema, document)

        then:
        validationResult.size() == 0
    }

    def "invalid query for array element"() {

        def query = """
             query{
                field(arg: ["stringVal"])
             } 
          """

        when:
        def document = Parser.parse(query)
        def validationResult = new Validator().validateDocument(schema, document)

        then:
        validationResult.size() == 1
    }


    def "invalid query for type"() {

        def query = """
             query{
                field(arg: 1)
             } 
          """

        when:
        def document = Parser.parse(query)
        def validationResult = new Validator().validateDocument(schema, document)

        then:
        validationResult.size() == 1
    }
}
