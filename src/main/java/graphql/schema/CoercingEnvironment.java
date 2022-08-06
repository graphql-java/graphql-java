package graphql.schema;

import java.util.Locale;

public interface CoercingEnvironment<T> {

    T getValueToBeCoerced();

    Locale getLocale();


    static <T> Builder<T> newCoercingEnvironment() {
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
                public Locale getLocale() {
                    return locale;
                }
            };
        }
    }
}
