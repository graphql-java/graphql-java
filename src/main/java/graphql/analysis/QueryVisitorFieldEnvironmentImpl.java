package graphql.analysis;

import java.util.Map;
import java.util.Objects;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.SelectionSetContainer;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

@Internal
public class QueryVisitorFieldEnvironmentImpl implements QueryVisitorFieldEnvironment {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLCompositeType parentType;
    private final Map<String, Object> arguments;
    private final QueryVisitorFieldEnvironment parentEnvironment;
    private final SelectionSetContainer selectionSetContainer;

    QueryVisitorFieldEnvironmentImpl(QueryVisitorFieldEnvironment environment) {
        this(environment.getField(),
                environment.getFieldDefinition(),
                environment.getParentType(),
                environment.getParentEnvironment(),
                environment.getArguments(),
                environment.getSelectionSetContainer());
    }

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

        return Objects.equals(field, that.field)
                && Objects.equals(fieldDefinition, that.fieldDefinition)
                && Objects.equals(parentType, that.parentType)
                && Objects.equals(parentEnvironment, that.parentEnvironment)
                && Objects.equals(arguments, that.arguments);
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
