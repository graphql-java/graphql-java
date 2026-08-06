package graphql.execution;

import graphql.Internal;
import graphql.schema.Coercing;
import graphql.schema.SchemaEnumValue;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaScalar;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@Internal
@NullMarked
public interface InputValueSchema {

    List<? extends SchemaInputField> getInputFields(
            SchemaInputObject inputObjectType);

    Coercing<?, ?> getScalarCoercing(SchemaScalar scalarType);

    Object getEnumRuntimeValue(SchemaEnumValue enumValue);
}
