package graphql.execution.instrumentation.dataloader.models;

import org.jetbrains.annotations.NotNull;

public class Suburb {
    private final String id;
    private final String name;

    public Suburb(@NotNull String id, @NotNull String name) {
        this.id = id;
        this.name = name;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

}
