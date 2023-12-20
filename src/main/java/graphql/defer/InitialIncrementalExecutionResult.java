package graphql.defer;

import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import org.reactivestreams.Publisher;

@ExperimentalApi
public interface InitialIncrementalExecutionResult extends ExecutionResult {
    boolean hasNext();

    Publisher<IncrementalItem> getIncrementalItemPublisher();
}
