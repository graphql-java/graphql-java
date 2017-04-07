package graphql.schema.somepackage;

public class TestClass {

    private String privateProperty = "privateValue";

    private String publicProperty = "publicValue";

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
