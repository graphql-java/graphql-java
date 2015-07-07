package graphql.schema;


public class GraphQLScalarType implements GraphQLType,GraphQLInputType,GraphQLOutputType{
    String name;
    String description;

    private Coercing coercing;

    public GraphQLScalarType(){

    }

    public GraphQLScalarType(String name, String description, Coercing coercing) {
        this.name = name;
        this.description = description;
        this.coercing = coercing;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Coercing getCoercing() {
        return coercing;
    }

    public void setCoercing(Coercing coercing) {
        this.coercing = coercing;
    }
}
