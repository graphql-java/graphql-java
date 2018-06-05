package graphql.execution.instrumentation.dataloader.models;

public class Company {

    private final int id;
    private final String name;

    public Company(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
