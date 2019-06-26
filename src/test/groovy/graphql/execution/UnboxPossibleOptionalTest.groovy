package graphql.execution

import spock.lang.Specification

/**
 *
 * @author Sean Brandt [ sean@bwcgroup.com ]
 */
class UnboxPossibleOptionalTest extends Specification {

  def "should find default unboxers"() {
    when:
    UnboxPossibleOptional.UNBOXERS  // Force class load, and thus service provider load

    then:
    UnboxPossibleOptional.UNBOXERS.size() == 2
    UnboxPossibleOptional.UNBOXERS.find( { u -> u.class == UnboxPossibleOptional.JavaOptionalUnboxer.class} ) != null
    UnboxPossibleOptional.UNBOXERS.find( { u -> u.class == TestOptionalUnboxer.class} ) != null

  }

  def "will unbox an Optional"() {
    given:
    def data = "test"
    def optional = Optional.of(data)

    when:
    def result = UnboxPossibleOptional.unboxPossibleOptional(optional)

    then:
    result == data
  }

  def "will pass input back as output for non optional"() {
    given:
    def data = "test"

    when:
    def result = UnboxPossibleOptional.unboxPossibleOptional(data)

    then:
    result == data
  }
}
