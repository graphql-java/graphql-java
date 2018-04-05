package graphql.execution.instrumentation.dataloader.models;

import java.util.List;

public class Department {
    private final String id;
    private final String name;
    private final List<String> productIds;

    public Department(String id, String name, List<String> productIds) {
        this.id = id;
        this.name = name;
        this.productIds = productIds;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getProductIds() {
        return productIds;
    }

    @Override
    public String toString() {
        return "Department{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", productIds=" + productIds +
                '}';
    }
}
