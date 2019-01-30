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
        def e = thrown(InvalidSyntaxException)

        e.location.line == 13
        e.location.column == 4
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
        def e = thrown(InvalidSyntaxException)
        print e

        e.location.line == 2
        e.location.column == 12
    }

    def "short query failure is ok"() {
        def query = '''query X { field1 field2(thisBreaksHere field3 }'''
        when:
        new Parser().parseDocument(query)
        then:
        def e = thrown(InvalidSyntaxException)

        e.location.line == 1
        e.location.column == 39
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
