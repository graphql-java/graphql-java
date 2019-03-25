package graphql.i18n

/**
 * This test helper will assert the formatted messages are quality
 */
@SuppressWarnings("UnnecessaryQualifiedReference")
// ^ not sure but IDEA groovyc wont compile this otherwise
class AssertingI18N extends I18N {

    AssertingI18N(I18N.BundleType bundleType, Locale locale) {
        super(bundleType, locale)
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
        def patterb = super.getResourceBundle().getString(msgKey)
        def msg = super.msg(msgKey, msgArgs)
        replacementsAreMade(msgKey, msg)
        allReplacementsArgsAreUsed(msgKey, patterb, msgArgs.length)
        return msg
    }

    static replacementsAreMade(String msgKey, String msg) {
        for (int i = 0; i < 100; i++) {
            // make sure all {0} .. {n} replacements are actually replaced
            def badReplacement = msg.contains("{$i")
            assert !badReplacement, "The I18N message $msgKey did not replace all variable markers : $msg"
        }
    }

    static allReplacementsArgsAreUsed(String msgKey, String pattern, replacementArgsLen) {
        int count = 0;
        for (int i = 0; i < 100; i++) {
            if (pattern.contains("{$i}")) {
                count++
            }
        }
        assert count == replacementArgsLen, "The I18N message $msgKey did not replace all arguments passed to it \
 expected $replacementArgsLen but it has $count replacement tokens: $pattern"
    }

}
