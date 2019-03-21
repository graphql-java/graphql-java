package graphql.i18n

/**
 * This test helper will assert the formatted messages are quality
 */
@SuppressWarnings("UnnecessaryQualifiedReference")
// ^ not sure but IDEA groovyc wont compile this otherwise
class AssertingI18N extends I18N {

    AssertingI18N(I18N.BundleType bundleType, Locale locale) {
        super(bundleType, locale)
        assertBundleStaticShape()
    }

    static I18N validationBundle() {
        return i18n(I18N.BundleType.Validation, Locale.getDefault())
    }

    static I18N i18n(I18N.BundleType bundleType) {
        return i18n(bundleType, Locale.getDefault())
    }

    static I18N i18n(I18N.BundleType bundleType, Locale locale) {
        return new AssertingI18N(bundleType, locale)
    }

    @Override
    String msg(String msgKey, Object... msgArgs) {
        def msg = super.msg(msgKey, msgArgs)
        replacementsAreMade(msgKey, msg)
        return msg
    }

    static replacementsAreMade(String msgKey, String msg) {
        for (int i = 0; i < 100; i++) {
            // make sure all {0} .. {n} replacements are actually replaced
            def badReplacement = msg.contains("{$i")
            assert !badReplacement, "The I18N message $msgKey did not replace all variable markers : $msg"
        }
    }

    def assertBundleStaticShape() {
        def enumeration = this.resourceBundle.getKeys()
        while (enumeration.hasMoreElements()) {
            def msgKey = enumeration.nextElement()
            def pattern = this.resourceBundle.getString(msgKey)
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
