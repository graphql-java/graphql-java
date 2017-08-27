package graphql.analysis;

import graphql.Internal;
import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

@Internal
public class QueryPath {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLCompositeType parentType;
    private final QueryPath parentPath;

    public QueryPath(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parentType, QueryPath parentPath) {
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
        this.parentPath = parentPath;
    }

    public Field getField() {
        return field;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public QueryPath getParentPath() {
        return parentPath;
    }

    public GraphQLCompositeType getParentType() {
        return parentType;
    }
}
