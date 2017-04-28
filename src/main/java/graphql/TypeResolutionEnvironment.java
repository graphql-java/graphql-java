package graphql;

import java.util.Map;

import graphql.language.Field;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

public class TypeResolutionEnvironment {

    private final Object object;
    private final Map<String, Object> arguments;
    private final Field field;
    private final GraphQLType fieldType;
    private final GraphQLSchema schema;

    public TypeResolutionEnvironment(Object object, Map<String, Object> arguments, Field field, GraphQLType fieldType, GraphQLSchema schema) {
        this.object = object;
        this.arguments = arguments;
        this.field = field;
        this.fieldType = fieldType;
        this.schema = schema;
    }

    public Object getObject() {
        return object;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Field getField() {
        return field;
    }

    public GraphQLType getFieldType() {
        return fieldType;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }
}
