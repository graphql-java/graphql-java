package graphql.execution;

import graphql.Internal;
import graphql.schema.Coercing;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.SchemaEnumValue;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaScalar;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

@Internal
@NullMarked
public final class GraphQLInputValueSchema
        implements InputValueSchema {

    private final GraphqlFieldVisibility fieldVisibility;

    public GraphQLInputValueSchema(
            GraphqlFieldVisibility fieldVisibility) {
        this.fieldVisibility = assertNotNull(fieldVisibility);
    }

    @Override
    public List<? extends SchemaInputField> getInputFields(
            SchemaInputObject inputObjectType) {
        assertTrue(
                inputObjectType instanceof GraphQLInputObjectType,
                "Expected a GraphQLInputObjectType");
        return fieldVisibility.getFieldDefinitions(
                (GraphQLInputObjectType) inputObjectType);
    }

    @Override
    public Coercing<?, ?> getScalarCoercing(SchemaScalar scalarType) {
        assertTrue(
                scalarType instanceof GraphQLScalarType,
                "Expected a GraphQLScalarType");
        return ((GraphQLScalarType) scalarType).getCoercing();
    }

    @Override
    public Object getEnumRuntimeValue(
            SchemaEnumValue enumValue) {
        assertTrue(
                enumValue instanceof GraphQLEnumValueDefinition,
                "Expected a GraphQLEnumValueDefinition");
        return assertNotNull(
                ((GraphQLEnumValueDefinition) enumValue).getValue(),
                "The enum value '%s' must have a runtime value",
                enumValue.getName());
    }
}
