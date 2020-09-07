package graphql.execution;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FutureValueUnboxer implements ValueUnboxer {

    @Override
    public Object unbox(Object object, ValueUnboxingContext context) {
        if (object instanceof Future) {
            try {
                return context.unbox(((Future<?>) object).get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("The future must have been completed by this time.");
            } catch (ExecutionException e) {
                throw (RuntimeException) e.getCause();
            }
        }
        return object;
    }
}
