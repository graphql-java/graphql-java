package graphql.execution.instrumentation.dataloader.models;

public class Person {

    private final int id;
    private final String name;
    private final int companyId;

    public Person(int id, String name, int companyId) {
        this.id = id;
        this.name = name;
        this.companyId = companyId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCompanyId() {
        return companyId;
    }
}
