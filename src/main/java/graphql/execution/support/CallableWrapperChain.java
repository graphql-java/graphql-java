package graphql.execution.support;

import java.util.Iterator;
import java.util.concurrent.Callable;

/**
 * A {@link CallableWrapper} that can be used to wrap a {@link Callable} in multiple wrappers.
 */
public class CallableWrapperChain implements CallableWrapper {

    private final Iterator<CallableWrapper> wrappers;

    public CallableWrapperChain(Iterator<CallableWrapper> wrappers) {
        this.wrappers = wrappers;
    }

    @Override
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        Callable<T> result = callable;
        while (wrappers.hasNext()) {
            result = wrappers.next().wrapCallable(result);
        }
        return result;
    }
}
