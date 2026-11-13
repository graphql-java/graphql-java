package graphql.parser

import graphql.GraphQL
import graphql.InvalidSyntaxError
import graphql.StarWarsSchema
import graphql.TestUtil
import graphql.language.SourceLocation
import graphql.parser.exceptions.ParseCancelledTooManyNumericLiteralCharactersException
import graphql.schema.DataFetcher
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

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

        e.location.line == 13
        e.location.column == 5
        e.sourcePreview == '''fragment X on SomeType {
    fragField1
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

        e.location.line == 6
        e.location.column == 5
        e.location.sourceName == "part2"
        e.sourcePreview == '''fragment X on SomeType {
    fragField1
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
        Reader reader = MultiSourceReader.newMultiSourceReader()
                .string(sdl, "namedSource")
                .build()
        new Parser().parseDocument(reader)
        then:
        def e = thrown(InvalidSyntaxException)

        e.location.line == 2
        e.location.column == 13
        e.location.sourceName == "namedSource"
    }

    def "short query failure is ok"() {
        def query = '''query X { field1 field2(thisBreaksHere field3 }'''
        when:
        new Parser().parseDocument(query)
        then:
        def e = thrown(InvalidSyntaxException)

        e.location.line == 1
        e.location.column == 40
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

    def "numeric limit errors preserve source mapping and locale #locale"() {
        given:
        def source = MultiSourceReader.newMultiSourceReader()
                .string("type Query { f: String }\n", "query.graphqls")
                .string("input Filter {\n  amount: Int = ${'9' * 101}\n}\n", "filter.graphqls")
                .build()
        def environment = ParserEnvironment.newParserEnvironment()
                .document(source)
                .parserOptions(ParserOptions.getDefaultSdlParserOptions())
                .locale(locale)
                .build()

        when:
        Parser.parse(environment)

        then:
        def exception = thrown(ParseCancelledTooManyNumericLiteralCharactersException)
        exception.location == new SourceLocation(2, 17, 'filter.graphqls')
        exception.message.contains(message)
        exception.offendingToken == null

        where:
        locale         | message
        Locale.ENGLISH | 'more than 100 characters'
        Locale.GERMAN  | 'mehr als 100 Zeichen'
    }

    def "standalone value parsing translates numeric limit errors"() {
        when:
        Parser.parseValue('9' * 101)

        then:
        def exception = thrown(ParseCancelledTooManyNumericLiteralCharactersException)
        exception.location == new SourceLocation(1, 1)
    }

    def "execution reports a numeric limit error without fetching data - #description"() {
        given:
        def fetcher = Mock(DataFetcher)
        def graphQL = TestUtil.graphQL('type Query { f(arg: Float): String }', [Query: [f: fetcher]]).build()
        def options = ParserOptions.getDefaultOperationParserOptions().transform {
            it.maxNumericLiteralCharacters(5)
        }
        def input = newExecutionInput().query("{\n  f(arg: $numericInput)\n}")
                .graphQLContext([(ParserOptions): options])
                .locale(Locale.ENGLISH)
                .build()

        when:
        def result = graphQL.execute(input)

        then:
        !result.dataPresent
        result.errors.size() == 1
        result.errors[0] instanceof InvalidSyntaxError
        result.errors[0].message.contains('more than 5 characters')
        result.errors[0].locations == [new SourceLocation(2, 10)]
        0 * fetcher.get(_)

        where:
        description          | numericInput
        'integer'            | '123456'
        'malformed exponent' | '1e' + '9' * 10_000 + 'x'
    }

}
