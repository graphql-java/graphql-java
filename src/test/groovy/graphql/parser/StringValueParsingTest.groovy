package graphql.parser

import spock.lang.Specification

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

    def "parsing emoji should work"() {
        // needs surrogate pairs for this emoji
        given:
        def input = '''"\\ud83c\\udf7a"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''🍺''' // contains the beer icon 	U+1F37A  : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "parsing simple unicode should work"() {
        given:
        def input = '''"\\u56fe"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''图'''
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

    def "remove common indentation works as per spec"() {
        given:
        def input = '''


        line A
       line B
      line C
     line D
     line E
      line F
       line G
        line H


''' // 2 empty lines at the start and end

        when:
        String parsed = StringValueParsing.removeIndentation(input)

        then:
        def expected = '''    line A
   line B
  line C
 line D
 line E
  line F
   line G
    line H'''
        parsed == expected

    }
}
