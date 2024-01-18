package graphql.execution.incremental;

import graphql.ExperimentalApi;

import javax.annotation.Nullable;

@ExperimentalApi
public class DeferExecution {
    private final String label;

    public DeferExecution(String label) {
        this.label = label;
    }

    @Nullable
    public String getLabel() {
        return label;
    }
}
