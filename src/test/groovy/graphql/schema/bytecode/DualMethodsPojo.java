package graphql.schema.bytecode;

public class DualMethodsPojo {

    String name;

    public String getName() {
        return name;
    }

    public Boolean isName() {
        return false;
    }
}
