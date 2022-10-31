package graphql.i18n;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.VisibleForTesting;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

@Internal
public class I18n {


    /**
     * This enum is a type safe way to control what resource bundle to load from
     */
    public enum BundleType {
        Parsing,
        Scalars,
        Validation,
        Execution,
        General;

        private final String baseName;

        BundleType() {
            this.baseName = "i18n." + this.name();
        }
    }

    private final ResourceBundle resourceBundle;
    private final Locale locale;

    @VisibleForTesting
    protected I18n(BundleType bundleType, Locale locale) {
        assertNotNull(bundleType);
        assertNotNull(locale);
        this.locale = locale;
        this.resourceBundle = ResourceBundle.getBundle(bundleType.baseName, locale);
    }

    public Locale getLocale() {
        return locale;
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public static I18n i18n(BundleType bundleType, Locale locale) {
        return new I18n(bundleType, locale);
    }


    /**
     * Creates an I18N message using the key and arguments
     *
     * @param msgKey  the key in the underlying message bundle
     * @param msgArgs the message arguments
     *
     * @return the formatted I18N message
     */
    public String msg(String msgKey, Object... msgArgs) {
        return msgImpl(msgKey, msgArgs);
    }

    /**
     * Creates an I18N message using the key and arguments
     *
     * @param msgKey  the key in the underlying message bundle
     * @param msgArgs the message arguments
     *
     * @return the formatted I18N message
     */
    public String msg(String msgKey, List<Object> msgArgs) {
        return msgImpl(msgKey, msgArgs.toArray());
    }

    private String msgImpl(String msgKey, Object[] msgArgs) {
        String msgPattern = null;
        try {
            msgPattern = resourceBundle.getString(msgKey);
        } catch (MissingResourceException e) {
            assertShouldNeverHappen("There must be a resource bundle key called %s", msgKey);
        }
        return new MessageFormat(msgPattern).format(msgArgs);
    }
}
