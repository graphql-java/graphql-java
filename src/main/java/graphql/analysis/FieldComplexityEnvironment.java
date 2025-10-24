package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

@PublicApi
@NullMarked
public class FieldComplexityEnvironment {
    private final Field field;
    private final GraphQLFieldDefinition fieldDefinition;
    private final GraphQLCompositeType parentType;
    private final @Nullable FieldComplexityEnvironment parentEnvironment;
    private final Map<String, Object> arguments;

    public FieldComplexityEnvironment(Field field, GraphQLFieldDefinition fieldDefinition, GraphQLCompositeType parentType, Map<String, Object> arguments, @Nullable FieldComplexityEnvironment parentEnvironment) {
        this.field = field;
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
        this.arguments = arguments;
        this.parentEnvironment = parentEnvironment;
    }

    public Field getField() {
        return field;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public GraphQLCompositeType getParentType() {
        return parentType;
    }

    public @Nullable FieldComplexityEnvironment getParentEnvironment() {
        return parentEnvironment;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "FieldComplexityEnvironment{" +
            "field=" + field +
            ", fieldDefinition=" + fieldDefinition +
            ", parentType=" + parentType +
            ", arguments=" + arguments +
            '}';
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldComplexityEnvironment that = (FieldComplexityEnvironment) o;
        return Objects.equals(field, that.field)
            && Objects.equals(fieldDefinition, that.fieldDefinition)
            && Objects.equals(parentType, that.parentType)
            && Objects.equals(parentEnvironment, that.parentEnvironment)
            && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(field);
        result = 31 * result + Objects.hashCode(fieldDefinition);
        result = 31 * result + Objects.hashCode(parentType);
        result = 31 * result + Objects.hashCode(parentEnvironment);
        result = 31 * result + Objects.hashCode(arguments);
        return result;
    }
}


