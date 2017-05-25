package graphql.parser

import spock.lang.Specification

class GraphqlAntlrToLanguageTest extends Specification {

    def "parsing quoted string should work"() {
        given:
        def input = '''"simple quoted"'''

        when:
        String parsed = GraphqlAntlrToLanguage.parseString(input)

        then:
        parsed == "simple quoted"
    }

    def "parsing escaped json should work"() {
        given:
        def input = '''"{\"name\": \"graphql\", \"year\": 2015}"'''

        when:
        String parsed = GraphqlAntlrToLanguage.parseString(input)

        then:
        parsed == '''{\"name\": \"graphql\", \"year\": 2015}'''
    }

    def "parsing quoted quote should work"() {
        given:
        def input = '''"""'''

        when:
        String parsed = GraphqlAntlrToLanguage.parseString(input)

        then:
        parsed == '''"'''
    }

    def "parsing emoji should work"() {
        // needs surrogate pairs for this emoji
        given:
        def input = '''"\\ud83c\\udf7a"'''

        when:
        String parsed = GraphqlAntlrToLanguage.parseString(input)

        then:
        parsed == '''🍺''' // contains the beer icon 	U+1F37A  : http://www.charbase.com/1f37a-unicode-beer-mug
    }

    def "parsing simple unicode should work"() {
        given:
        def input = '''"\\u56fe"'''

        when:
        String parsed = GraphqlAntlrToLanguage.parseString(input)

        then:
        parsed == '''图'''
    }
}
