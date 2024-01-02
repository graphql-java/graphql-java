package graphql.incremental;

import graphql.ExperimentalApi;

import java.util.List;

@ExperimentalApi
public interface DelayedIncrementalExecutionResult {
    List<IncrementalItem> getIncremental();

    String getLabel();

    boolean hasNext();
}
