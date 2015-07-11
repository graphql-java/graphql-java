package graphql.schema;


public class GraphQLEnumValueDefinition {

    private final String name;
    private final String description;
    private final Object value;

    public GraphQLEnumValueDefinition(String name, String description, Object value) {
        this.name = name;
        this.description = description;
        this.value = value;
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
}
