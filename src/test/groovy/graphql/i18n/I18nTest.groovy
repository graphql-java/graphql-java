package graphql.i18n

import graphql.AssertException
import graphql.ExecutionInput
import graphql.TestUtil
import graphql.i18n.I18n.BundleType
import spock.lang.Specification

class I18nTest extends Specification {

    def "missing resource keys cause an assert"() {
        def i18n = I18n.i18n(BundleType.Validation, Locale.ENGLISH)
        when:
        i18n.msg("nonExistent")
        then:
        thrown(AssertException)
    }

    def "missing resource bundles default to a base version"() {
        // see https://saimana.com/list-of-country-locale-code/

        def expected = "Validation error ({0}) : Type '{1}' definition is not executable"

        when:
        def i18n = I18n.i18n(BundleType.Validation, Locale.ENGLISH)
        def msg = i18n.msg("ExecutableDefinitions.notExecutableType")

        then:
        msg == expected

        when:
        i18n = I18n.i18n(BundleType.Validation, Locale.CHINESE)
        msg = i18n.msg("ExecutableDefinitions.notExecutableType")
        then:
        msg == expected

        when:
        i18n = I18n.i18n(BundleType.Validation, new Locale("en", "IN")) // India
        msg = i18n.msg("ExecutableDefinitions.notExecutableType")
        then:
        msg == expected

        when:
        i18n = I18n.i18n(BundleType.Validation, new Locale("en", "FJ")) // Fiji
        msg = i18n.msg("ExecutableDefinitions.notExecutableType")
        then:
        msg == expected

        when:
        i18n = I18n.i18n(BundleType.Validation, new Locale("")) // Nothing
        msg = i18n.msg("ExecutableDefinitions.notExecutableType")
        then:
        msg == expected
    }

    def "all enums have resources and decent shapes"() {
        when:
        def bundleTypes = BundleType.values()
        then:
        for (BundleType bundleType : (bundleTypes)) {
            // Currently only testing the default English bundles
            def i18n = I18n.i18n(bundleType, Locale.ENGLISH)
            assert i18n.resourceBundle != null
            assertBundleStaticShape(i18n.resourceBundle)
        }
    }

    def "A non-default bundle can be read"() {
        def i18n = I18n.i18n(BundleType.Validation, Locale.GERMAN)
        when:
        def message = i18n.msg("ExecutableDefinitions.notExecutableType")
        then:
        message == "Validierungsfehler ({0}) : Type definition '{1}' ist nicht ausführbar"
    }

    def "integration test of valid messages"() {
        def sdl = """
            type Query {
                field(arg : Int) : Subselection
            }
            
            type Subselection {
                name : String
            }
        """
        def graphQL = TestUtil.graphQL(sdl).build()


        when:
        def locale = new Locale("en", "IN")
        def ei = ExecutionInput.newExecutionInput().query("query missingSubselectionQ { field(arg : 1) }")
                .locale(locale)
                .build()
        def er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Validation error (SubselectionRequired@[field]) : Subselection required for type 'Subselection' of field 'field'"

        when:
        locale = Locale.GERMANY
        ei = ExecutionInput.newExecutionInput().query("query missingSubselectionQ { field(arg : 1) }")
                .locale(locale)
                .build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Validierungsfehler (SubselectionRequired@[field]) : Unterauswahl erforderlich für Typ 'Subselection' des Feldes 'field'"

        when:
        locale = Locale.getDefault()
        ei = ExecutionInput.newExecutionInput().query("query missingSubselectionQ { field(arg : 1) }")
                .locale(locale)
                .build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Validation error (SubselectionRequired@[field]) : Subselection required for type 'Subselection' of field 'field'"

        when:
        // no locale - it should default
        ei = ExecutionInput.newExecutionInput().query("query missingSubselectionQ { field(arg : 1) }")
                .build()
        er = graphQL.execute(ei)
        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Validation error (SubselectionRequired@[field]) : Subselection required for type 'Subselection' of field 'field'"
    }

    static def assertBundleStaticShape(ResourceBundle bundle) {
        def enumeration = bundle.getKeys()
        while (enumeration.hasMoreElements()) {
            def msgKey = enumeration.nextElement()
            def pattern = bundle.getString(msgKey)
            quotesAreBalanced(msgKey, pattern, '\'')
            quotesAreBalanced(msgKey, pattern, '"')
            curlyBracesAreBalanced(msgKey, pattern)
            noStringFormatPercentLeftOver(msgKey, pattern)
            placeHoldersNotRepeated(msgKey, pattern)
        }
    }

    static quotesAreBalanced(String msgKey, String msg, String c) {
        def quoteCount = msg.count(c)
        assert quoteCount % 2 == 0, "The I18n message $msgKey quotes are unbalanced : $msg"
    }

    static placeHoldersNotRepeated(String msgKey, String msg) {
        for (int i = 0; i < 200; i++) {
            def count = msg.count("{$i}")
            assert count < 2, "The I18n message $msgKey has repeated positional placeholders : $msg"
        }
    }

    static noStringFormatPercentLeftOver(String msgKey, String msg) {
        assert !msg.contains("%s"), "The I18n message $msgKey has a %s in it : $msg"
        assert !msg.contains("%d"), "The I18n message $msgKey has a %d in it : $msg"
    }

    static def curlyBracesAreBalanced(String msgKey, String msg) {
        def leftCount = msg.count("{")
        def rightCount = msg.count("}")
        if (leftCount > 0 || rightCount > 0) {
            assert leftCount == rightCount, "The I18n message $msgKey left curly quote are unbalanced : $msg"
        }
    }

}