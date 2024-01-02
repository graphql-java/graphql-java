package graphql.incremental;

import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import org.reactivestreams.Publisher;

@ExperimentalApi
public interface IncrementalExecutionResult extends ExecutionResult {
    boolean hasNext();

    Publisher<DelayedIncrementalExecutionResult> getIncrementalItemPublisher();
}
