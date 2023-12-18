package graphql.normalized.incremental;

import graphql.ExperimentalApi;

import javax.annotation.Nullable;

@ExperimentalApi
public class DeferExecution {
    private final String label;

    public DeferExecution(@Nullable String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
