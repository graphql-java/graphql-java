package graphql.parser

import graphql.TestUtil
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.SourceLocation
import graphql.language.StringValue
import spock.lang.Specification

class LineTerminatorTest extends Specification {

    def "AST locations and comments support #label"() {
        given:
        def query = ['{', '  first # comment 😀', '  second', '}'].join(lineTerminator)

        when:
        def document = new Parser().parseDocument(new StringReader(query))
        def operation = document.getDefinitionsOfType(OperationDefinition)[0]
        def fields = operation.selectionSet.getSelectionsOfType(Field)

        then:
        fields[0].sourceLocation == new SourceLocation(2, 3)
        fields[1].sourceLocation == new SourceLocation(3, 3)
        fields[1].comments*.content == [' comment 😀']

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "syntax errors after #label retain code point columns"() {
        when:
        Parser.parse('{ f(arg: "😀")' + lineTerminator + '  f(arg: "😀") ) }')

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(2, 15)

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "lexer errors after #label have the right location"() {
        when:
        Parser.parse('{ first' + lineTerminator + '  ? }')

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(2, 3)

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "EOF after a comment and #label starts a new line"() {
        when:
        Parser.parse('{ first # comment' + lineTerminator)

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(2, 1)

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "block string locations support #label"() {
        when:
        Parser.parse('{ f(arg: """first' + lineTerminator + 'second""")' + lineTerminator + '  ) }')

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(3, 3)

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "block string values normalize #label before removing indentation"() {
        given:
        def input = ['"""', '  first', '    second', '', '  third', '"""'].join(lineTerminator)

        when:
        def value = Parser.parseValue(input) as StringValue

        then:
        value.value == 'first\n  second\n\nthird'

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "captured ignored characters preserve #label"() {
        given:
        def options = ParserOptions.newParserOptions().captureIgnoredChars(true).build()
        def environment = ParserEnvironment.newParserEnvironment()
                .document('{' + lineTerminator + '  first }')
                .parserOptions(options)
                .build()

        when:
        def document = Parser.parse(environment)
        def field = document.getDefinitionsOfType(OperationDefinition)[0].selectionSet.getSelectionsOfType(Field)[0]

        then:
        field.sourceLocation == new SourceLocation(2, 3)
        field.ignoredChars.left*.value.join() == lineTerminator + '  '

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "multi source error locations support #label"() {
        given:
        def reader = MultiSourceReader.newMultiSourceReader()
                .string('{ first }' + lineTerminator, 'first')
                .string('{' + lineTerminator + '  )' + lineTerminator + '}', 'second')
                .build()

        when:
        new Parser().parseDocument(reader)

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(2, 3, 'second')

        where:
        label  | lineTerminator
        'LF'   | '\n'
        'CR'   | '\r'
        'CRLF' | '\r\n'
    }

    def "mixed and consecutive line terminators count CRLF once"() {
        when:
        Parser.parse('# comment\r\r\n\n\r{ ) }')

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(5, 3)
    }

    def "CRLF split across reader buffers counts once"() {
        when:
        Parser.parse('#' + ('a' * 4094) + '\r\n{ ) }')

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(2, 3)
    }

    def "validation errors use CR line boundaries"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { first: String }').build()

        when:
        def result = graphQL.execute('{ first\r  unknown }')

        then:
        result.errors.size() == 1
        result.errors[0].locations == [new SourceLocation(2, 3)]
    }

    def "Unicode separator #codePoint is not ignored between tokens"() {
        when:
        Parser.parse('{ first' + separator + ' second }')

        then:
        def error = thrown(InvalidSyntaxException)
        error.location == new SourceLocation(1, 8)

        where:
        codePoint | separator
        'U+2028'  | '\u2028'
        'U+2029'  | '\u2029'
    }

    def "Unicode separator #codePoint remains valid in strings and comments"() {
        given:
        def text = 'before' + separator + 'after'

        expect:
        (Parser.parseValue('"' + text + '"') as StringValue).value == text
        (Parser.parseValue('"""' + text + '"""') as StringValue).value == text

        when:
        def document = Parser.parse('#' + text + '\n{ first }')
        def operation = document.getDefinitionsOfType(OperationDefinition)[0]

        then:
        operation.sourceLocation == new SourceLocation(2, 1)
        operation.comments*.content == [text]

        where:
        codePoint | separator
        'U+2028'  | '\u2028'
        'U+2029'  | '\u2029'
    }

    def "escaped line terminators in ordinary strings retain their value"() {
        expect:
        (Parser.parseValue('"\\r\\n"') as StringValue).value == '\r\n'
    }
}
