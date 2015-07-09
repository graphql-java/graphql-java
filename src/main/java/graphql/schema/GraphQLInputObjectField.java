package graphql.schema;


public class GraphQLInputObjectField {

    private final String name;
    private final GraphQLInputType type;
    private final Object defaultValue;

    public GraphQLInputObjectField(String name, GraphQLInputType type) {
        this(name, type, null);
    }

    public GraphQLInputObjectField(String name, GraphQLInputType type, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}