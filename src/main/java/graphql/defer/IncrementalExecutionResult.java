package graphql.defer;

import graphql.ExperimentalApi;

import java.util.List;

@ExperimentalApi
public interface IncrementalExecutionResult {
    List<IncrementalItem> getIncremental();

    String getLabel();

    boolean hasNext();
}
