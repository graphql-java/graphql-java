package graphql.schema;


public class GraphQLEnumType implements GraphQLType,GraphQLInputType,GraphQLOutputType{

    String name;

    private Coercing coercing;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Coercing getCoercing() {
        return coercing;
    }

    public void setCoercing(Coercing coercing) {
        this.coercing = coercing;
    }
}
