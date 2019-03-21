package graphql.validation.rules

import graphql.i18n.AssertingI18N
import graphql.i18n.I18N
import graphql.validation.ValidationContext
import spock.lang.Specification

class ValidationRuleTest extends Specification {

    def assertingI18N = assertingI18N()

    /**
     * This will cause the the i18n code to run and hence catch any formatting
     * problems in the actual bundle files etc.. eg unclosed i18n format symbols
     *
     */
    ValidationContext mockValidationContext() {

        ValidationContext validationContext = Mock(ValidationContext) {

            //noinspection GroovyAssignabilityCheck
            _ * i18n(*_) >> { args ->
                //noinspection GroovyAssignabilityCheck
                def msg = assertingI18N.msg(args[0] as String, args[1] as Object[])
                return msg
            }

            _ * getI18n() >> assertingI18N

        }
        validationContext
    }

    I18N assertingI18N() {
        AssertingI18N.validationBundle()
    }

}
