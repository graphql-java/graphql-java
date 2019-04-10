package graphql.i18n


import graphql.AssertException
import graphql.i18n.I18N.BundleType
import spock.lang.Specification

class I18NTest extends Specification {
    def i18n = I18N.i18n(BundleType.Validation, Locale.getDefault())

    def "missing resource keys cause an assert"() {
        when:
        i18n.msg("nonExistent")
        then:
        thrown(AssertException)
    }

    def "all enums have resources and decent shapes"() {
        when:
        def bundleTypes = BundleType.values()
        then:
        for (BundleType bundleType : (bundleTypes)) {
            def i18n = I18N.i18n(BundleType.Validation, Locale.getDefault())
            assert i18n.resourceBundle != null
            assertBundleStaticShape(i18n.resourceBundle)
        }
    }

    def assertBundleStaticShape(ResourceBundle bundle) {
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
        assert quoteCount % 2 == 0, "The I18N message $msgKey quotes are unbalanced : $msg"
    }

    static placeHoldersNotRepeated(String msgKey, String msg) {
        for (int i = 0; i < 100; i++) {
            def count = msg.count("{$i}")
            assert count < 2, "The I18N message $msgKey has repeated positional placeholders : $msg"
        }
    }

    static noStringFormatPercentLeftOver(String msgKey, String msg) {
        assert !msg.contains("%s"), "The I18N message $msgKey has a %s in it : $msg"
        assert !msg.contains("%d"), "The I18N message $msgKey has a %d in it : $msg"
    }

    static def curlyBracesAreBalanced(String msgKey, String msg) {
        def leftCount = msg.count("{")
        def rightCount = msg.count("}")
        if (leftCount > 0 || rightCount > 0) {
            assert leftCount == rightCount, "The I18N message $msgKey left curly quote are unbalanced : $msg"
        }
    }

}
