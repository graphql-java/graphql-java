package graphql.execution.instrumentation.dataloader;

public class Product {
    private final String id;
    private final String name;
    private final int suppliedById;

    public Product(String id, String name, int suppliedById) {
        this.id = id;
        this.name = name;
        this.suppliedById = suppliedById;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSuppliedById() {
        return suppliedById;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
