package graphql.analysis;

import graphql.Internal;
import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

import java.util.Map;

@Internal
public class QueryVisitorEnvironment {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLCompositeType parent;
    private final QueryPath path;
    private final Map<String, Object> arguments;

    public QueryVisitorEnvironment(Field field,
                                   GraphQLFieldDefinition fieldDefinition,
                                   GraphQLCompositeType parent,
                                   QueryPath path,
                                   Map<String, Object> arguments) {
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.parent = parent;
        this.path = path;
        this.arguments = arguments;
    }

    public Field getField() {
        return field;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public GraphQLCompositeType getParentType() {
        return parent;
    }

    public QueryPath getPath() {
        return path;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }


    @Override
    public String toString() {
        return "QueryVisitorEnvironment{" +
                "field=" + field +
                ", fieldDefinition=" + fieldDefinition +
                ", parent=" + parent +
                ", path=" + path +
                ", arguments=" + arguments +
                '}';
    }
}
