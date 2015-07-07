package graphql.schema;


public class GraphQLFieldDefinition {

    String name;
    GraphQLOutputType type;
    Object defaultValue;
    ResolveValue resolveValue;

    public GraphQLFieldDefinition() {

    }

    public GraphQLFieldDefinition(String name, GraphQLOutputType type, Object defaultValue, ResolveValue resolveValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.resolveValue = resolveValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GraphQLOutputType getType() {
        return type;
    }

    public void setType(GraphQLOutputType type) {
        this.type = type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public ResolveValue getResolveValue() {
        return resolveValue;
    }

    public void setResolveValue(ResolveValue resolveValue) {
        this.resolveValue = resolveValue;
    }
}
