package graphql.execution.instrumentation.dataloader.models;

import java.util.List;

public class Shop {
    private final String id;
    private final String name;
    private final List<String> departmentIds;

    public Shop(String id, String name, List<String> departmentIds) {
        this.id = id;
        this.name = name;
        this.departmentIds = departmentIds;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getDepartmentIds() {
        return departmentIds;
    }

    @Override
    public String toString() {
        return "Shop{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", departmentIds=" + departmentIds +
                '}';
    }
}
