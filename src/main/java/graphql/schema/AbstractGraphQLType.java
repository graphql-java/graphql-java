package graphql.schema;

import static graphql.Assert.assertNotNull;

public class AbstractGraphQLType implements GraphQLType {
    private final String name;

    public AbstractGraphQLType(String name) {
        assertNotNull(name, "name can't null");
        this.name = name;
    }

    public String getName() {
        return name.equals("") ? null : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractGraphQLType that = (AbstractGraphQLType) o;

        return getName().equals(that.getName());

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
