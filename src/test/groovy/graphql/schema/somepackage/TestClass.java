package graphql.schema.somepackage;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class TestClass {

    private String privateProperty = "privateValue";

    private String publicProperty = "publicValue";

    public String publicField = "publicFieldValue";

    private String privateField = "privateFieldValue";

    public static TestClass createPackageProtectedImpl(String value) {
        return new PackageProtectedImpl(value);
    }

    public String getPackageProtectedProperty() {
        return "parentProperty";
    }

    private String getPrivateProperty() {
        return privateProperty;
    }

    public String getPublicProperty() {
        return publicProperty;
    }
}
