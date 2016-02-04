package graphql.schema;


import static graphql.Assert.assertNotNull;

public class GraphQLScalarType implements GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType,GraphQLNullableType {

    private final String name;
    private final String description;
    private final Coercing coercing;

    public GraphQLScalarType(String name, String description, Coercing coercing) {
        assertNotNull(name, "name can't null");
        assertNotNull(coercing, "coercing can't be null");
        this.name = name;
        this.description = description;
        this.coercing = coercing;
    }


    public String getDescription() {
        return description;
    }


    public Coercing getCoercing() {
        return coercing;
    }

    @Override
    public String toString() {
        return "GraphQLScalarType{" +
                "name='" + getName() + '\'' +
                ", description='" + description + '\'' +
                ", coercing=" + coercing +
                '}';
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphQLType that = (GraphQLType) o;

        return getName().equals(that.getName());

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
