package graphql.execution;

import graphql.PublicApi;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Public API because it should be used as a delegate when implementing a custom {@link ValueUnboxer}
 */
@PublicApi
public class OptionalValueUnboxer implements ValueUnboxer {

    @Override
    public Object unbox(Object result, ValueUnboxingContext context) {
        if (result instanceof Optional) {
            Optional<?> optional = (Optional<?>) result;
            return optional.map(context::unbox).orElse(null);
        } else if (result instanceof OptionalInt) {
            OptionalInt optional = (OptionalInt) result;
            if (optional.isPresent()) {
                return context.unbox(optional.getAsInt());
            } else {
                return null;
            }
        } else if (result instanceof OptionalDouble) {
            OptionalDouble optional = (OptionalDouble) result;
            if (optional.isPresent()) {
                return context.unbox(optional.getAsDouble());
            } else {
                return null;
            }
        } else if (result instanceof OptionalLong) {
            OptionalLong optional = (OptionalLong) result;
            if (optional.isPresent()) {
                return context.unbox(optional.getAsLong());
            } else {
                return null;
            }
        }

        return result;
    }
}
