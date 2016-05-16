package graphql.schema.somepackage;

class PackageProtectedImpl extends TestClass {

    private final String aValue;

    PackageProtectedImpl(String aValue) {
        this.aValue = aValue;
    }

    @Override
    public String getPackageProtectedProperty() {
        return aValue;
    }

    public String getPropertyOnlyDefinedOnPackageProtectedImpl() {
        return "valueOnlyDefinedOnPackageProtectedIpl";
    }

}
