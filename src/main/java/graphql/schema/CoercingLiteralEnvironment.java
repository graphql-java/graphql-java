package graphql.schema;

import graphql.language.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * This interface represent the parameters that are sent into AST literal {@link Coercing} methods
 * such as {@link Coercing#parseLiteral(CoercingLiteralEnvironment)}
 */
public interface CoercingLiteralEnvironment extends CoercingEnvironment<Value<?>> {
    /**
     * @return the request variables in place
     */
    Map<String, Object> getVariables();


    /**
     * Allows you to build a new coercing literal environment
     *
     * @return a new builder of CoercingLiteralEnvironment
     */
    static Builder newLiteralEnvironment() {
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
                public @NotNull Locale getLocale() {
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
