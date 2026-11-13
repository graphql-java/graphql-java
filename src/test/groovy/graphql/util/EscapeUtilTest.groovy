package graphql.util


import spock.lang.Specification

class EscapeUtilTest extends Specification {

    def "1105 - encoding of json strings"() {
        when:
        def json = EscapeUtil.escapeJsonString(strValue)

        then:
        json == expected

        where:
        strValue                                  | expected
        ''                                        | ''
        'json'                                    | 'json'
        'quotation-"'                             | 'quotation-\\"'
        'reverse-solidus-\\'                      | 'reverse-solidus-\\\\'
        'backspace-\b'                            | 'backspace-\\b'
        'formfeed-\f'                             | 'formfeed-\\f'
        'newline-\n'                              | 'newline-\\n'
        'carriage-return-\r'                      | 'carriage-return-\\r'
        'horizontal-tab-\t'                       | 'horizontal-tab-\\t'

        // this is some AST from issue 1105
        '''"{"operator":"eq", "operands": []}"''' | '''\\"{\\"operator\\":\\"eq\\", \\"operands\\": []}\\"'''
    }

    def "escapeJsonStringTo produces correct escaped output"() {
        given:
        def strValue = new StringBuilder()

        when:
        EscapeUtil.escapeJsonStringTo(strValue, input)

        then:
        strValue.toString() == expected

        where:
        input                                       | expected
        ''                                          | ''
        'plain'                                     | 'plain'
        'quote-"and\\'                              | 'quote-\\"and\\\\'
        'tab\tnewline\n'                            | 'tab\\tnewline\\n'
        'combo-"\\\b\f\n\r\t'                       | 'combo-\\"\\\\\\b\\f\\n\\r\\t'
    }


}
