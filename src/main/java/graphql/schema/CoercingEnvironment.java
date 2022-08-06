package graphql.schema;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * This interface represent the parameters that are sent into a {@link Coercing} methods
 *
 * @param <T> for two
 */
public interface CoercingEnvironment<T> {

    /**
     * @return the value to be coerced in some way
     */
    T getValueToBeCoerced();

    /**
     * @return a locale that can be used during coercion
     */
    @NotNull
    Locale getLocale();


    /**
     * Allows you to build a new coercing environment
     *
     * @param <T> for two
     *
     * @return a new builder of CoercingEnvironment
     */
    static <T> Builder<T> newEnvironment() {
        return new Builder<>();
    }

    class Builder<T> {
        T value;
        Locale locale = Locale.getDefault();

        public Builder<T> value(T value) {
            this.value = value;
            return this;
        }

        public Builder<T> locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public CoercingEnvironment<T> build() {
            return new CoercingEnvironment<T>() {
                @Override
                public T getValueToBeCoerced() {
                    return value;
                }

                @Override
                public @NotNull Locale getLocale() {
                    return locale;
                }
            };
        }
    }
}
