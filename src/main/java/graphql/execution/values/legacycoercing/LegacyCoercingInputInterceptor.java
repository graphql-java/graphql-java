package graphql.execution.values.legacycoercing;

import graphql.GraphQLContext;
import graphql.Scalars;
import graphql.execution.values.InputInterceptor;
import graphql.scalar.CoercingUtil;
import graphql.schema.GraphQLInputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static graphql.Assert.assertNotNull;
import static graphql.scalar.CoercingUtil.isNumberIsh;

public class LegacyCoercingInputInterceptor implements InputInterceptor {

    /**
     * This will ONLY observe legacy values and invoke the callback when it gets one.  you can use this to enumerate how many
     * legacy values are hitting you graphql implementation
     *
     * @param observerCallback a callback allowing you to observe a legacy scalar value
     *
     * @return an InputInterceptor that only observes values
     */
    public static LegacyCoercingInputInterceptor observesValues(BiConsumer<Object, GraphQLInputType> observerCallback) {
        return new LegacyCoercingInputInterceptor(((input, graphQLInputType) -> {
            observerCallback.accept(input, graphQLInputType);
            return input;
        }));
    }

    /**
     * This will change legacy values as it encounters them to something acceptable to the more strict coercion rules.
     *
     * @return an InputInterceptor that migrates values to a more strict value
     */
    public static LegacyCoercingInputInterceptor migratesValues() {
        return migratesValues((input, type) -> {
        });
    }

    /**
     * This will change legacy values as it encounters them to something acceptable to the more strict coercion rules.
     * The observer callback will be invoked if it detects a legacy value that it will change.
     *
     * @param observerCallback a callback allowing you to observe a legacy scalar value before it is migrated
     *
     * @return an InputInterceptor that both observes values and migrates them to a more strict value
     */
    public static LegacyCoercingInputInterceptor migratesValues(BiConsumer<Object, GraphQLInputType> observerCallback) {
        return new LegacyCoercingInputInterceptor(((input, graphQLInputType) -> {
            observerCallback.accept(input, graphQLInputType);
            if (Scalars.GraphQLBoolean.equals(graphQLInputType)) {
                return coerceLegacyBooleanValue(input);
            }
            if (Scalars.GraphQLFloat.equals(graphQLInputType)) {
                return coerceLegacyFloatValue(input);
            }
            if (Scalars.GraphQLInt.equals(graphQLInputType)) {
                return coerceLegacyIntValue(input);
            }
            if (Scalars.GraphQLString.equals(graphQLInputType)) {
                return coerceLegacyStringValue(input);
            }
            return input;
        }));
    }

    private final BiFunction<Object, GraphQLInputType, Object> behavior;

    private LegacyCoercingInputInterceptor(BiFunction<Object, GraphQLInputType, Object> behavior) {
        this.behavior = assertNotNull(behavior);
    }

    @Override
    public Object intercept(@Nullable Object input, @NotNull GraphQLInputType graphQLType, @NotNull GraphQLContext graphqlContext, @NotNull Locale locale) {
        if (isLegacyValue(input, graphQLType)) {
            // we ONLY apply the new behavior IF it's an old acceptable legacy value.
            // so for compliant values - we change nothing and invoke no behaviour
            // and for values that would not coerce anyway, we also invoke no behavior
            return behavior.apply(input, graphQLType);
        }
        return input;
    }

    @SuppressWarnings("RedundantIfStatement")
    static boolean isLegacyValue(Object input, GraphQLInputType graphQLType) {
        if (Scalars.GraphQLBoolean.equals(graphQLType)) {
            return isLegacyBooleanValue(input);
        } else if (Scalars.GraphQLFloat.equals(graphQLType)) {
            return isLegacyFloatValue(input);
        } else if (Scalars.GraphQLInt.equals(graphQLType)) {
            return isLegacyIntValue(input);
        } else if (Scalars.GraphQLString.equals(graphQLType)) {
            return isLegacyStringValue(input);
        } else {
            return false;
        }
    }

    static boolean isLegacyBooleanValue(Object input) {
        return input instanceof String || CoercingUtil.isNumberIsh(input);
    }

    static boolean isLegacyFloatValue(Object input) {
        return input instanceof String;
    }

    static boolean isLegacyIntValue(Object input) {
        return input instanceof String;
    }

    static boolean isLegacyStringValue(Object input) {
        return !(input instanceof String);
    }

    static Object coerceLegacyBooleanValue(Object input) {
        if (input instanceof String) {
            String lStr = ((String) input).toLowerCase();
            if (lStr.equals("true")) {
                return true;
            }
            if (lStr.equals("false")) {
                return false;
            }
            return input;
        } else if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                // this should never happen because String is handled above
                return input;
            }
            return value.compareTo(BigDecimal.ZERO) != 0;
        }
        // unchanged
        return input;
    }

    static Object coerceLegacyFloatValue(Object input) {
        if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return input;
            }
            return value.doubleValue();
        }
        return input;
    }

    static Object coerceLegacyIntValue(Object input) {
        if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return input;
            }
            try {
                return value.intValueExact();
            } catch (ArithmeticException e) {
                return input;
            }
        }
        return input;
    }


    static Object coerceLegacyStringValue(Object input) {
        return String.valueOf(input);
    }
}
