package graphql.schema.validation


import spock.lang.Specification

class SchemaValidatorTest extends Specification {


    def "check used rules"() {
        when:
        def validator = new SchemaValidator()
        def rules = validator.rules
        then:
        rules.size() == 10
        rules[0] instanceof NoUnbrokenInputCycles
        rules[1] instanceof NoDefaultValueCircularRefs
        rules[2] instanceof TypesImplementInterfaces
        rules[3] instanceof TypeAndFieldRule
        rules[4] instanceof DefaultValuesAreValid
        rules[5] instanceof AppliedDirectivesAreValid
        rules[6] instanceof AppliedDirectiveArgumentsAreValid
        rules[7] instanceof InputAndOutputTypesUsedAppropriately
        rules[8] instanceof OneOfInputObjectRules
        rules[9] instanceof DeprecatedInputObjectAndArgumentsAreValid
    }
}
