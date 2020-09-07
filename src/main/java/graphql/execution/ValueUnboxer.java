package graphql.execution;

import graphql.GraphQLError;
import graphql.PublicSpi;
import graphql.schema.DataFetchingEnvironment;

/**
 * A value unboxer takes values that are wrapped in classes like {@link java.util.Optional} / {@link java.util.OptionalInt} etc..
 * and returns value from them.  You can provide your own implementation if you have your own specific
 * holder classes.
 */
@PublicSpi
public interface ValueUnboxer {

    /**
     * The default value unboxer handles JDK classes such as {@link java.util.Optional} and {@link java.util.OptionalInt},
     * as well classes which are used internally during field fetching.
     */
    ValueUnboxers DEFAULT =
            new ValueUnboxers(new FutureValueUnboxer(), new OptionalValueUnboxer(), new DataFetcherResultUnboxer());

    /**
     * Unboxes 'object' if it is boxed in an {@link java.util.Optional } like
     * type that this unboxer can handle. Otherwise returns its input
     * unmodified. Errors can be registered during unboxing, and local context can be modified.
     *
     * @param object to unbox
     * @param valueUnboxingContext context allowing recursive unboxing and handling errors
     * @return unboxed object, or original if cannot unbox
     */
    Object unbox(Object object, ValueUnboxingContext valueUnboxingContext);

    interface ValueUnboxingContext {
        Object unbox(Object object);

        void addError(GraphQLError error);

        void setLocalContext(Object localContext);

        DataFetchingEnvironment getDataFetchingEnvironment();
    }
}
