package graphql.schema.validation

import spock.lang.Specification

class SchemaValidationErrorCollectorTest extends Specification {

    def "collects distinct errors in insertion order"() {
        given:
        def collector = new SchemaValidationErrorCollector()
        def first = new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, "first")
        def second = new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue, "second")

        when:
        collector.addError(first)
        collector.addError(second)
        collector.addError(new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, "first"))

        then:
        collector.getErrors() as List == [first, second]
    }

    def "containsValidationError matches on classification"() {
        given:
        def collector = new SchemaValidationErrorCollector()

        expect:
        !collector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)

        when:
        collector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue, "invalid"))
        collector.addError(new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, "cycle"))

        then:
        collector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
        !collector.containsValidationError(SchemaValidationErrorType.OneOfNotInhabited)
    }
}
