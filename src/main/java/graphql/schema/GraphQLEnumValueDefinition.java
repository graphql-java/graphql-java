package graphql.schema;


import static graphql.Assert.assertNotNull;

public class GraphQLEnumValueDefinition {

    private final String name;
    private final String description;
    private final Object value;
    private final String deprecationReason;

    public GraphQLEnumValueDefinition(String name, String description, Object value, String deprecationReason) {
        assertNotNull(name, "name can't be null");
        this.name = name;
        this.description = description;
        this.value = value;
        this.deprecationReason = deprecationReason;
    }

    public GraphQLEnumValueDefinition(String name, String description, Object value) {
        this(name, description, value, null);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Object getValue() {
        return value;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }
}
