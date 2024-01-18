package graphql.execution.defer;

import graphql.ExperimentalApi;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.incremental.DeferExecution;

import java.util.Map;

@ExperimentalApi
public class DeferredSelectionSet extends MergedSelectionSet {
    private final DeferExecution deferExecution;

    private DeferredSelectionSet(DeferExecution deferExecution, Map<String, MergedField> subFields) {
        super(subFields);
        this.deferExecution = deferExecution;
    }

    public DeferExecution getDeferExecution() {
        return deferExecution;
    }
}
