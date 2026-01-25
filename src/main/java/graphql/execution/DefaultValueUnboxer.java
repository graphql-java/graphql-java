package graphql.execution;

import graphql.Internal;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Public API because it should be used as a delegate when implementing a custom {@link ValueUnboxer}
 */
@PublicApi
@NullMarked
public class DefaultValueUnboxer implements ValueUnboxer {


    @Override
    @SuppressWarnings("NullAway") // Interface not yet annotated, but can return null from empty Optional
    public Object unbox(final Object object) {
        return unboxValue(object);
    }

    @Internal // used by next-gen at the moment
    public static @Nullable Object unboxValue(Object result) {
        if (result instanceof Optional) {
            Optional optional = (Optional) result;
            return optional.orElse(null);
        } else if (result instanceof OptionalInt) {
            OptionalInt optional = (OptionalInt) result;
            if (optional.isPresent()) {
                return optional.getAsInt();
            } else {
                return null;
            }
        } else if (result instanceof OptionalDouble) {
            OptionalDouble optional = (OptionalDouble) result;
            if (optional.isPresent()) {
                return optional.getAsDouble();
            } else {
                return null;
            }
        } else if (result instanceof OptionalLong) {
            OptionalLong optional = (OptionalLong) result;
            if (optional.isPresent()) {
                return optional.getAsLong();
            } else {
                return null;
            }
        }

        return result;
    }
}