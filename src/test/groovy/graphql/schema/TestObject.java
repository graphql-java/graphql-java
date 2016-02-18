package graphql.schema;

public class TestObject {

    public final String someSuperField;

    public TestObject(
        String someSuperField) {

        this.someSuperField = someSuperField;

    }

    public String getSomeSuperField() {

        return someSuperField;
    }
}
