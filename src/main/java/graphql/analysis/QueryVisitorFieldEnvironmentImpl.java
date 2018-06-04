package graphql.analysis;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.SelectionSetContainer;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

import java.util.Map;

@Internal
public class QueryVisitorFieldEnvironmentImpl implements QueryVisitorFieldEnvironment {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLCompositeType parentType;
    private final Map<String, Object> arguments;
    private final QueryVisitorFieldEnvironment parentEnvironment;
    private final SelectionSetContainer selectionSetContainer;

    public QueryVisitorFieldEnvironmentImpl(Field field,
                                            GraphQLFieldDefinition fieldDefinition,
                                            GraphQLCompositeType parentType,
                                            QueryVisitorFieldEnvironment parentEnvironment,
                                            Map<String, Object> arguments,
                                            SelectionSetContainer selectionSetContainer) {
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
        this.parentEnvironment = parentEnvironment;
        this.arguments = arguments;
        this.selectionSetContainer = selectionSetContainer;
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    @Override
    public GraphQLCompositeType getParentType() {
        return parentType;
    }

    @Override
    public QueryVisitorFieldEnvironment getParentEnvironment() {
        return parentEnvironment;
    }

    @Override
    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public SelectionSetContainer getSelectionSetContainer() {
        return selectionSetContainer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueryVisitorFieldEnvironmentImpl that = (QueryVisitorFieldEnvironmentImpl) o;

        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (fieldDefinition != null ? !fieldDefinition.equals(that.fieldDefinition) : that.fieldDefinition != null)
            return false;
        if (parentType != null ? !parentType.equals(that.parentType) : that.parentType != null) return false;
        if (parentEnvironment != null ? !parentEnvironment.equals(that.parentEnvironment) : that.parentEnvironment != null)
            return false;
        return arguments != null ? arguments.equals(that.arguments) : that.arguments == null;
    }

    @Override
    public int hashCode() {
        int result = field != null ? field.hashCode() : 0;
        result = 31 * result + (fieldDefinition != null ? fieldDefinition.hashCode() : 0);
        result = 31 * result + (parentType != null ? parentType.hashCode() : 0);
        result = 31 * result + (parentEnvironment != null ? parentEnvironment.hashCode() : 0);
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "QueryVisitorFieldEnvironmentImpl{" +
                "field=" + field +
                ", fieldDefinition=" + fieldDefinition +
                ", parentType=" + parentType +
                ", parentEnvironment=" + parentEnvironment +
                ", arguments=" + arguments +
                '}';
    }
}
