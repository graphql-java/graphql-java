package graphql.schema;


import graphql.language.Field;

import java.util.List;
import java.util.Map;

public class DataFetchingEnvironment {
    private final Object source;
    private final Map<String, Object> arguments;
    private final Object context;
    private final List<Field> fields;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;

    public DataFetchingEnvironment(Object source, Map<String, Object> arguments, Object context, List<Field> fields, GraphQLOutputType fieldType, GraphQLType parentType, GraphQLSchema graphQLSchema) {
        this.source = source;
        this.arguments = arguments;
        this.context = context;
        this.fields = fields;
        this.fieldType = fieldType;
        this.parentType = parentType;
        this.graphQLSchema = graphQLSchema;
    }

    public Object getSource() {
        return source;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public boolean containsArgument(String name) {
        return arguments.containsKey(name);
    }

    public <T> T getArgument(String name) {
        return (T) arguments.get(name);
    }

    public Object getContext() {
        return context;
    }

    public List<Field> getFields() {
        return fields;
    }

    public GraphQLOutputType getFieldType() {
        return fieldType;
    }

    public GraphQLType getParentType() {
        return parentType;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }
}
