package graphql.execution.instrumentation;

import graphql.Contract;
import graphql.PublicSpi;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An {@link Instrumentation} implementation can create this as a stateful object that is then passed
 * to each instrumentation method, allowing state to be passed down with the request execution
 *
 * @see Instrumentation#createStateAsync(InstrumentationCreateStateParameters)
 */
@NullMarked
@PublicSpi
public interface InstrumentationState {

    /**
     * This helper method allows you to cast from {@link InstrumentationState} to a custom classes more easily.
     *
     * @param rawState the raw InstrumentationState
     * @param <T>      for two
     *
     * @return a cast custom InstrumentationState, or null when rawState is null
     */
    @Contract("!null -> !null")
    static <T extends InstrumentationState> @Nullable T ofState(@Nullable InstrumentationState rawState) {
        //noinspection unchecked
        return (T) rawState;
    }
}
