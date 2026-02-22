package graphql.execution.instrumentation;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * An implementation of {@link graphql.execution.instrumentation.Instrumentation} that does nothing.  It can be used
 * as a base for derived classes where you only implement the methods you want to. With all the methods in {@link Instrumentation}
 * now defaulted (post Java 6) this class is really not needed anymore but has been retained for backwards compatibility
 * reasons.
 *
 * @deprecated use {@link SimplePerformantInstrumentation} instead as a base class.
 */
@PublicApi
@NullMarked
@Deprecated(since = "2022-10-05")
public class SimpleInstrumentation implements Instrumentation {

    /**
     * A singleton instance of a {@link graphql.execution.instrumentation.Instrumentation} that does nothing
     */
    public static final SimpleInstrumentation INSTANCE = new SimpleInstrumentation();

}
