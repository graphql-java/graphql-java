package graphql.parser

import graphql.GraphQL
import graphql.InvalidSyntaxError
import graphql.StarWarsSchema
import spock.lang.Specification

class ParserExceptionTest extends Specification {
    def badQueryPart1 = '''
query X {
       field1
       field2
       field3
       field4
       field5
}'''

    def badQueryPart2 = '''

fragment X on SomeType {
    fragField1
    fragField2(syntaxErrorHere
    fragField3
    fragField4
    fragField5
}
        '''

    def badQuery = badQueryPart1 + badQueryPart2

    def "builds specific exception with preview when in error"() {
        when:
        new Parser().parseDocument(badQuery)
        then:
        def e = thrown(InvalidSyntaxException)

        e.location.line == 14
        e.location.column == 4
        e.sourcePreview == '''    fragField1
    fragField2(syntaxErrorHere
    fragField3
    fragField4
    fragField5
}
        
'''
    }

    def "can work with multi source input"() {
        when:
        def multiSource = MultiSourceReader.newMultiSourceReader()
                .string(badQueryPart1, "part1")
                .string(badQueryPart2, "part2")
                .build()

        new Parser().parseDocument(multiSource)
        then:
        def e = thrown(InvalidSyntaxException)

        e.location.line == 7
        e.location.column == 4
        e.location.sourceName == "part2"
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
        new Parser().parseDocument(sdl, "namedSource")
        then:
        def e = thrown(InvalidSyntaxException)
        print e

        e.location.line == 3
        e.location.column == 12
        e.location.sourceName == "namedSource"
    }

    def "short query failure is ok"() {
        def query = '''query X { field1 field2(thisBreaksHere field3 }'''
        when:
        new Parser().parseDocument(query)
        then:
        def e = thrown(InvalidSyntaxException)

        e.location.line == 1
        e.location.column == 39
        e.location.sourceName == null
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
