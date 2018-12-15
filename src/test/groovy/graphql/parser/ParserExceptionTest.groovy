package graphql.parser

import graphql.GraphQL
import graphql.InvalidSyntaxError
import graphql.StarWarsSchema
import spock.lang.Specification

class ParserExceptionTest extends Specification {
    def badQuery = '''
query X {
       field1
       field2
       field3
       field4
       field5
}

fragment X on SomeType {
    fragField1
    fragField2(syntaxErrorHere
    fragField3
    fragField4
    fragField5
}
        '''

    def "builds specific exception with preview when in error"() {
        when:
        new Parser().parseDocument(badQuery)
        then:
        def e = thrown(InvalidSyntaxError)

        e.locations[0].line == 13
        e.locations[0].column == 4
        e.sourcePreview == '''    fragField1
    fragField2(syntaxErrorHere
    fragField3
    fragField4
    fragField5
}
        
'''
    }

    def "more parsing error tests"() {
        def sdl = '''
            scala Url   # spillin misteak

            interface Foo {
               is_foo : Boolean
            }
        '''
        when:
        new Parser().parseDocument(sdl)
        then:
        def e = thrown(InvalidSyntaxError)
        print e

        e.locations[0].line == 2
        e.locations[0].column == 12
    }

    def "short query failure is ok"() {
        def query = '''query X { field1 field2(thisBreaksHere field3 }'''
        when:
        new Parser().parseDocument(query)
        then:
        def e = thrown(InvalidSyntaxError)

        e.locations[0].line == 1
        e.locations[0].column == 39
    }

    def "integration test of parse exception handling "() {
        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build()
        when:
        def er = graphQL.execute(badQuery)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof InvalidSyntaxError

    }

}
