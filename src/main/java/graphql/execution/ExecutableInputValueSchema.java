package graphql.execution;

import graphql.Internal;
import graphql.schema.Coercing;
import graphql.schema.ExecutableSchema;
import graphql.schema.SchemaEnumValue;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaScalar;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static graphql.Assert.assertNotNull;

@Internal
@NullMarked
public final class ExecutableInputValueSchema
        implements InputValueSchema {

    private final ExecutableSchema schema;

    public ExecutableInputValueSchema(ExecutableSchema schema) {
        this.schema = assertNotNull(schema);
    }

    @Override
    public List<? extends SchemaInputField> getInputFields(
            SchemaInputObject inputObjectType) {
        return schema.getInputFields(inputObjectType);
    }

    @Override
    public Coercing<?, ?> getScalarCoercing(SchemaScalar scalarType) {
        return schema.getScalarCoercing(scalarType);
    }

    @Override
    public Object getEnumRuntimeValue(
            SchemaEnumValue enumValue) {
        return schema.getEnumRuntimeValue(enumValue);
    }
}
