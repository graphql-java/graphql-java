package graphql.schema;

import graphql.language.Value;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public interface CoercingLiteralEnvironment extends CoercingEnvironment<Value<?>> {
    Map<String, Object> getVariables();


    static Builder newCoercingEnvironment() {
        return new Builder();
    }

    class Builder {
        Value<?> value;
        Locale locale = Locale.getDefault();

        Map<String, Object> variables = Collections.emptyMap();

        public Builder value(Value<?> value) {
            this.value = value;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public CoercingLiteralEnvironment build() {
            return new CoercingLiteralEnvironment() {
                @Override
                public Value<?> getValueToBeCoerced() {
                    return value;
                }

                @Override
                public Locale getLocale() {
                    return locale;
                }

                @Override
                public Map<String, Object> getVariables() {
                    return variables;
                }
            };
        }
    }
}
