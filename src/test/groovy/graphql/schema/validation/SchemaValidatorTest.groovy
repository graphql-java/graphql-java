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
                NoDefaultValueCircularRefs,
                TypesImplementInterfaces,
                TypeAndFieldRule,
                DefaultValuesAreValid,
                AppliedDirectivesAreValid,
                AppliedDirectiveArgumentsAreValid,
                InputAndOutputTypesUsedAppropriately,
                OneOfInputObjectRules,
                DeprecatedInputObjectAndArgumentsAreValid,
        ]
        rules[1] !== nextRules[1]
    }
}
