package graphql.schema.validation


import spock.lang.Specification

class SchemaValidatorTest extends Specification {


    def "check used rules"() {
        when:
        def validator = new SchemaValidator()
        def rules = validator.rules
        then:
        rules.size() == 8
        rules[0] instanceof NoUnbrokenInputCycles
        rules[1] instanceof TypesImplementInterfaces
        rules[2] instanceof TypeAndFieldRule
        rules[3] instanceof DefaultValuesAreValid
        rules[4] instanceof AppliedDirectivesAreValid
        rules[5] instanceof AppliedDirectiveArgumentsAreValid
        rules[6] instanceof InputAndOutputTypesUsedAppropriately
        rules[7] instanceof DeprecatedInputObjectAndArgumentsAreValid
    }
}
