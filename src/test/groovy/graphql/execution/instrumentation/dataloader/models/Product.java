package graphql.execution.instrumentation.dataloader.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Product {
    private final String id;
    private final String name;
    private final int suppliedById;
    private final List<Integer> madeByIds;

    public Product(String id, String name, int suppliedById) {
        this(id, name, suppliedById, Collections.emptyList());
    }

    public Product(String id, String name, int suppliedById, List<Integer> madeByIds) {
        this.id = id;
        this.name = name;
        this.suppliedById = suppliedById;
        this.madeByIds = new ArrayList<>(madeByIds);
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

    public List<Integer> getMadeByIds() {
        return madeByIds;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
