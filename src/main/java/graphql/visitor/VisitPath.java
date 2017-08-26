package graphql.visitor;

import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;

public class VisitPath {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final VisitPath parent;

    public VisitPath(Field field, GraphQLFieldDefinition fieldDefinition, VisitPath parent) {
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.parent = parent;
    }

    public Field getField() {
        return field;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public VisitPath getParent() {
        return parent;
    }
}
