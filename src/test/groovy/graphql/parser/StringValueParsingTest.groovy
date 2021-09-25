package graphql.parser

import spock.lang.Specification

import static java.util.Arrays.asList
import static java.util.stream.Collectors.joining

class StringValueParsingTest extends Specification {

    def "parsing quoted string should work"() {
        given:
        def input = '''"simple quoted"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == "simple quoted"
    }

    def "parsing escaped json should work"() {
        given:
        def input = '''"{\"name\": \"graphql\", \"year\": 2015}"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''{\"name\": \"graphql\", \"year\": 2015}'''
    }

    def "parsing quoted quote should work"() {
        given:
        def input = '''"""'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''"'''
    }

    def "parsing beer stein as surrogate pair should work"() {
        given:
        def input = '''"\\ud83c\\udf7a"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''🍺''' // contains the beer icon 	U+1F37A  : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "parsing simple unicode should work - Basic Multilingual Plane (BMP)"() {
        given:
        def input = '''"\\u5564\\u9152"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''啤酒'''
    }

    def "parsing triple quoted string should work"() {
        given:
        def input = '''"""triple quoted"""'''

        when:
        String parsed = StringValueParsing.parseTripleQuotedString(input)

        then:
        parsed == '''triple quoted'''
    }

    /*
       From the spec :

        Multi-line strings are sequences of characters wrapped in triple-quotes (`"""`).
        White space, line terminators, and quote and backslash characters may all be
        used unescaped, enabling free form text. Characters must all be valid
        {SourceCharacter} to ensure printable source text. If non-printable ASCII
            characters need to be used, escape sequences must be used within standard
            double-quote strings.
     *
     */

    def "parsing triple quoted string where escaping should work"() {
        given:
        def input = '''"""| inner quoted \\""" part but with all others left as they are \\n with slash escaped chars \\b\\ud83c\\udf7a\\r\\t\\n |"""'''

        when:
        String parsed = StringValueParsing.parseTripleQuotedString(input)

        then:
        parsed == '''| inner quoted """ part but with all others left as they are \\n with slash escaped chars \\b\\ud83c\\udf7a\\r\\t\\n |'''
    }

    def joinLines(String... args) {
        asList(args).stream().collect(joining('\n'))
    }

    def "removes uniform indentation from a string"() {
        given:
        def input = joinLines(
                '',
                '    Hello,',
                '      World!',
                '',
                '    Yours,',
                '      GraphQL.',
        )

        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = joinLines('Hello,', '  World!', '', 'Yours,', '  GraphQL.')
        parsed == expected
    }

    def "removes empty leading and trailing lines"() {
        given:
        def input = joinLines(
                '',
                '',
                '    Hello,',
                '      World!',
                '',
                '    Yours,',
                '      GraphQL.',
                '',
                '',
        )

        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = joinLines('Hello,', '  World!', '', 'Yours,', '  GraphQL.')
        parsed == expected
    }

    def "removes blank leading and trailing lines"() {
        given:
        def input = joinLines(
                '  ',
                '        ',
                '    Hello,',
                '      World!',
                '',
                '    Yours,',
                '      GraphQL.',
                '        ',
                '  ',
        )

        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = joinLines('Hello,', '  World!', '', 'Yours,', '  GraphQL.')
        parsed == expected
    }

    def "retains indentation from first line"() {
        given:
        def input = joinLines(
                '    Hello,',
                '      World!',
                '',
                '    Yours,',
                '      GraphQL.',
        )

        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = joinLines('    Hello,', '  World!', '', 'Yours,', '  GraphQL.')
        parsed == expected
    }

    def "does not alter trailing spaces"() {
        given:
        def input = joinLines(
                '               ',
                '    Hello,     ',
                '      World!   ',
                '               ',
                '    Yours,     ',
                '      GraphQL. ',
                '               ',
        )

        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = joinLines(
                'Hello,     ',
                '  World!   ',
                '           ',
                'Yours,     ',
                '  GraphQL. ',
        )
        parsed == expected
    }

    def "1438 - losing one character when followed by a space is fixed"() {
        given:
        def input = '''L1
L 2
L 3'''
        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = '''L1
L 2
L 3'''
        parsed == expected
    }
}
