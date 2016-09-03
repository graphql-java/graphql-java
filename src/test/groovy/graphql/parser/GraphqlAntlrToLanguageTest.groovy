package graphql.parser

import spock.lang.Ignore
import spock.lang.Specification
/**
 * Created by jiayu on 9/3/16.
 */
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

  @Ignore("sadly this does not work yet")
  def "parsing emoji should work"() {
    given:
    def input = '''"\\u1f37a"'''

    when:
    String parsed = GraphqlAntlrToLanguage.parseString(input)

    then:
    parsed == '''🍺'''
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
