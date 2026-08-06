package graphql.execution.values;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.schema.GraphQLInputType;
import graphql.schema.SchemaInputType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * This INTERNAL class can be used to intercept input values before they are coerced into runtime values
 * by the {@link graphql.execution.ValuesResolver} code.
 * <p>
 * You could use it to observe input values and optionally change them.  Perhaps some sort of migration of data
 * needs to happen, and you need to know what data you are getting in type terms.  This would help you do that.
 * <p>
 * If this is present in a {@link GraphQLContext} it will be called.  By default, it is not present
 * so no calls to it will be made.
 * <p>
 * There is a performance aspect to using this code.  If you take too long to return values then you
 * are going to slow down your system depending on how big your input objects are.
 */
@Internal
public interface InputInterceptor {

    /**
     * This is called with a value that is to be presented to the {@link graphql.execution.ValuesResolver} code.  The values
     * may be scalars, enums and complex input types.
     *
     * @param value          the input value that can be null
     * @param graphQLType    the input type
     * @param graphqlContext the graphql context in play
     * @param locale         the locale in play
     *
     * @return a value that may differ from the original value
     */
    Object intercept(@Nullable Object value,
                     @NonNull GraphQLInputType graphQLType,
                     @NonNull GraphQLContext graphqlContext,
                     @NonNull Locale locale);

    /**
     * Intercepts a value described by a schema-neutral input type.
     *
     * <p>The default preserves existing interceptors for GraphQL schema types. Implementations
     * that need to intercept values from other executable schema views can override this method.</p>
     */
    default Object intercept(
            @Nullable Object value,
            @NonNull SchemaInputType inputType,
            @NonNull GraphQLContext graphqlContext,
            @NonNull Locale locale) {
        if (inputType instanceof GraphQLInputType) {
            return intercept(
                    value,
                    (GraphQLInputType) inputType,
                    graphqlContext,
                    locale);
        }
        return value;
    }
}
