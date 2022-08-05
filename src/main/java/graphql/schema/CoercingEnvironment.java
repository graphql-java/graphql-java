package graphql.schema;

import java.util.Locale;

public interface CoercingEnvironment<T> {

    T getValueToBeCoerced();

    Locale getLocale();

}
