package graphql.scalar;

import graphql.Internal;
import graphql.i18n.I18n;

import java.util.Locale;

@Internal
public class CoercingUtil {
    public static boolean isNumberIsh(Object input) {
        return input instanceof Number || input instanceof String;
    }

   public static String typeName(Object input) {
        if (input == null) {
            return "null";
        }

        return input.getClass().getSimpleName();
    }

    public static String i18nMsg(Locale locale, String msgKey, Object... args) {
        return I18n.i18n(I18n.BundleType.Scalars, locale).msg(msgKey, args);
    }
}
