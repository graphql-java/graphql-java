package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaEnum;
import graphql.schema.universe.SUEnumType;
import graphql.schema.universe.SUEnumValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public final class SUSchemaEnum
        extends AbstractSUSchemaNamedType implements SchemaEnum {

    @Internal
    public SUSchemaEnum(
            SUSchemaExecutableSchema executableSchema,
            SUEnumType type) {
        super(executableSchema, type);
    }

    @Override
    public List<SUSchemaEnumValue> getValues() {
        List<SUEnumValue> values = getExecutableSchema()
                .getSchema()
                .getEnumValues(getEnumTypeVertex());
        List<SUSchemaEnumValue> result = new ArrayList<>(values.size());
        for (SUEnumValue value : values) {
            result.add(new SUSchemaEnumValue(getExecutableSchema(), value));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable SUSchemaEnumValue getValue(String name) {
        SUEnumValue value = getExecutableSchema()
                .getSchema()
                .getEnumValue(getEnumTypeVertex(), name);
        if (value == null) {
            return null;
        }
        return new SUSchemaEnumValue(getExecutableSchema(), value);
    }

    @Internal
    public SUEnumType getEnumTypeVertex() {
        return (SUEnumType) getTypeVertex();
    }
}
