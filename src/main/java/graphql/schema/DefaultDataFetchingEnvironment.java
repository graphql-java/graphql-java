package graphql.schema;


import graphql.language.Field;

import java.util.List;
import java.util.Map;

public class DefaultDataFetchingEnvironment implements DataFetchingEnvironment {
    
    private final Object source;
    private final Map<String, Object> arguments;
    private final Object context;
    private final List<Field> fields;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;

    public DefaultDataFetchingEnvironment(Object source, Map<String, Object> arguments, Object context, List<Field> fields, GraphQLOutputType fieldType, GraphQLType parentType, GraphQLSchema graphQLSchema) {
        this.source = source;
        this.arguments = arguments;
        this.context = context;
        this.fields = fields;
        this.fieldType = fieldType;
        this.parentType = parentType;
        this.graphQLSchema = graphQLSchema;
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public boolean containsArgument(String name) {
        return arguments.containsKey(name);
    }

    @Override
    public <T> T getArgument(String name) {
        return (T) arguments.get(name);
    }

    @Override
    public Object getContext() {
        return context;
    }

    @Override
    public List<Field> getFields() {
        return fields;
    }

    @Override
    public GraphQLOutputType getFieldType() {
        return fieldType;
    }

    @Override
    public GraphQLType getParentType() {
        return parentType;
    }

    @Override
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }
}
