package graphql.schema;


import static graphql.Assert.assertNotNull;

/**
 * A special type to allow a object/interface types to reference itself. It's replaced with the real type
 * object when the schema is build.
 */
public class GraphQLTypeReference implements GraphQLOutputType {

    private final String name;

    public GraphQLTypeReference(String name) {
        assertNotNull(name, "name can't null");
        this.name = name;
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
