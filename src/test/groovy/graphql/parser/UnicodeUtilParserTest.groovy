package graphql.parser

import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.StringValue
import spock.lang.Ignore
import spock.lang.Specification

class UnicodeUtilParserTest extends Specification {
    /*
        Implements RFC to support full Unicode
        Original RFC https://github.com/graphql/graphql-spec/issues/687
        RFC spec text https://github.com/graphql/graphql-spec/pull/849
        RFC JS implementation https://github.com/graphql/graphql-js/pull/3117

        TL;DR
        Previously, valid SourceCharacters included Unicode scalar values up to and including U+FFFF - the Basic Multilingual Plane (BMP)
        Now this is changing to incorporate all Unicode scalar values
        Assert {value} is a within the *Unicode scalar value* range (>= 0x0000 and <= 0xD7FF or >= 0xE000 and <= 0x10FFFF).
        Practically this means you can have your beer emoji (U+1F37A) in queries as \\u{1F37A}
    */

    // With this RFC, code points outside the Basic Multilingual Plane can be parsed. For example, emojis
    // Previously emojis could only be parsed with surrogate pairs. Now they can be parsed with the code point directly
    @Ignore
    def "parsing beer stein as escaped unicode"() {
        given:
        def input = '''"\\u{1F37A} hello"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''🍺 hello''' // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    @Ignore
    def "parsing beer mug non escaped"() {
        given:
        def input = '''"🍺 hello"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''🍺 hello''' // contains the beer icon U+1F37A : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    @Ignore
    def "allow braced escaped unicode"() {
        def input = '''
              {
              foo(arg: "\\u{1F37A}")
               }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)
        def field = operationDefinition.getSelectionSet().getSelections()[0] as Field
        def argValue = field.arguments[0].value as StringValue

        then:
        argValue.getValue() == "🍺"
    }

    /*
        From the RFC:
        For legacy reasons, a *supplementary character* may be escaped by two
        fixed-width unicode escape sequences forming a *surrogate pair*. For example
        the input `"\\uD83D\\uDCA9"` is a valid {StringValue} which represents the same
        Unicode text as `"\\u{1F4A9}"`. While this legacy form is allowed, it should be
        avoided as a variable-width unicode escape sequence is a clearer way to encode
        such code points.
    */
    @Ignore
    def "allow surrogate pairs escaped unicode"() {
        def input = '''
              {
              foo(arg: "\\ud83c\\udf7a")
               }
        '''

        when:
        Document document = Parser.parse(input)
        OperationDefinition operationDefinition = (document.definitions[0] as OperationDefinition)
        def field = operationDefinition.getSelectionSet().getSelections()[0] as Field
        def argValue = field.arguments[0].value as StringValue

        then:
        argValue.getValue() == "🍺"
    }

    /*
        From the RFC:
        * If {leadingValue} is >= 0xD800 and <= 0xDBFF (a *Leading Surrogate*):
        * Assert {trailingValue} is >= 0xDC00 and <= 0xDFFF (a *Trailing Surrogate*).
        * Return ({leadingValue} - 0xD800) × 0x400 + ({trailingValue} - 0xDC00) + 0x10000.
     */
    @Ignore
    def "invalid surrogate pair"() {
        def input = '''
              {
              foo(arg: "\\uD83D\\uDBFF")
               }
        '''

        when:
        Document document = Parser.parse(input)

        then:
        // TODO: Raise exception
        false
    }

    @Ignore
    def "invalid unicode code point"() {
        def input = '''
              {
              foo(arg: "\\u{fffffff}")
               }
        '''

        when:
        Document document = Parser.parse(input)

        then:
        // TODO: Raise exception
        false
    }

    @Ignore
    def "invalid unpaired surrogate" () {
        def input = '''
              {
              foo(arg: "\\uD83D")
               }
        '''

        when:
        Document document = Parser.parse(input)

        then:
        // TODO: Discuss whether to raise exception
        false
    }

    @Ignore
    def "invalid code point - too long" () {
        given:
        def input = '''"\\u{000000000}"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        // TODO: Discuss whether to raise exception
        false
    }

    /*
        From the RFC
        **Byte order mark**

        UnicodeBOM :: "Byte Order Mark (U+FEFF)"

        The *Byte Order Mark* is a special Unicode code point which may appear at the
        beginning of a file which programs may use to determine the fact that the text
        stream is Unicode, and what specific encoding has been used.

        As files are often concatenated, a *Byte Order Mark* may appear anywhere within
        a GraphQL document and is {Ignored}.
    */
    @Ignore
    def "byte order mark to be ignored" () {
        // The Byte Order Mark indicates a Unicode stream, and whether the stream is high-endian or low-endian
        given:
        def input = '''"hello \\uFEFF\\u4F60\\u597D"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''hello 你好'''
    }

    // TODO: How do we want to handle control characters?
    @Ignore
    def "escapes zero byte" () {
        // TODO: This is a test case from the JS implementation. Do we want to implement this case?
        given:
        def input = '''"\\x00"'''

        when:
        String parsed = StringValueParsing.parseSingleQuotedString(input)

        then:
        parsed == '''\\u0000'''
    }
}

