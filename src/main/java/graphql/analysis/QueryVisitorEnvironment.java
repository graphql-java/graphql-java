package graphql.analysis;

import graphql.Internal;
import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

@Internal
public class QueryVisitorEnvironment {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLCompositeType parent;
    private final VisitPath path;

    public QueryVisitorEnvironment(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parent, VisitPath path) {
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.parent = parent;
        this.path = path;
    }

    public Field getField() {
        return field;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public GraphQLCompositeType getParent() {
        return parent;
    }

    public VisitPath getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "QueryVisitorEnvironment{" +
                "field=" + field +
                ", fieldDefinition=" + fieldDefinition +
                ", parent=" + parent +
                ", path=" + path +
                '}';
    }
}
