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

    @Override
    public String toString() {
        return "QueryPath{" +
                "field=" + field +
                ", fieldDefinition=" + fieldDefinition +
                ", parentType=" + parentType +
                ", parentPath=" + parentPath +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueryPath queryPath = (QueryPath) o;

        if (field != null ? !field.equals(queryPath.field) : queryPath.field != null) return false;
        if (fieldDefinition != null ? !fieldDefinition.equals(queryPath.fieldDefinition) : queryPath.fieldDefinition != null)
            return false;
        if (parentType != null ? !parentType.equals(queryPath.parentType) : queryPath.parentType != null) return false;
        return parentPath != null ? parentPath.equals(queryPath.parentPath) : queryPath.parentPath == null;
    }

    @Override
    public int hashCode() {
        int result = field != null ? field.hashCode() : 0;
        result = 31 * result + (fieldDefinition != null ? fieldDefinition.hashCode() : 0);
        result = 31 * result + (parentType != null ? parentType.hashCode() : 0);
        result = 31 * result + (parentPath != null ? parentPath.hashCode() : 0);
        return result;
    }
}
