package graphql.schema.validation


import spock.lang.Specification

class SchemaValidatorTest extends Specification {


    def "check used rules"() {
        when:
        def validator = new SchemaValidator()
        def rules = validator.rules
        def nextRules = validator.rules

        then:
        rules*.class == [
                NoUnbrokenInputCycles,
                TypesImplementInterfaces,
                TypeAndFieldRule,
                DefaultValuesAreValid,
                AppliedDirectivesAreValid,
                AppliedDirectiveArgumentsAreValid,
                InputAndOutputTypesUsedAppropriately,
                OneOfInputObjectRules,
                DeprecatedInputObjectAndArgumentsAreValid,
        ]
        (0..<rules.size()).every { rules[it] !== nextRules[it] }
    }
}
