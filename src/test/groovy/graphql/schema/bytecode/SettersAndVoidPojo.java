package graphql.schema.bytecode;

public class SettersAndVoidPojo {
    String name;

    public String getName() {
        return name;
    }

    public String getName(int arg) {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    void getSomething() {
    }
}
