package graphql.i18n;

import graphql.Internal;
import graphql.VisibleForTesting;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import static graphql.Assert.assertNotNull;

@Internal
public class I18N {

    /**
     * This enum is a type safe way to control what resource bundle to load from
     */
    public enum BundleType {
        Validation,
        Execution;

        private final String baseName;

        BundleType() {
            this.baseName = "i18n." + this.name();
        }
    }

    private final ResourceBundle resourceBundle;

    @VisibleForTesting
    protected I18N(BundleType bundleType, Locale locale) {
        assertNotNull(bundleType);
        assertNotNull(locale);
        this.resourceBundle = ResourceBundle.getBundle(bundleType.baseName, locale);
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public static I18N i18n(BundleType bundleType, Locale locale) {
        return new I18N(bundleType, locale);
    }


    /**
     * Creates an I18N message using the key and arguments
     *
     * @param msgKey  the key in the underlying message bundle
     * @param msgArgs the message arguments
     *
     * @return the formatted I18N message
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public String msg(String msgKey, Object... msgArgs) {
        String msgPattern = resourceBundle.getString(msgKey);
        assertNotNull(msgPattern, "There must be a resource bundle key called " + msgKey);

        String formattedMsg = new MessageFormat(msgPattern).format(msgArgs);
        return formattedMsg;
    }


}
