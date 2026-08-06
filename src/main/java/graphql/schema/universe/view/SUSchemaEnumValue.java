package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaEnumValue;
import graphql.schema.universe.SUEnumValue;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaEnumValue
        extends AbstractSUSchemaElement implements SchemaEnumValue {

    @Internal
    public SUSchemaEnumValue(
            SUExecutableSchema executableSchema,
            SUEnumValue value) {
        super(executableSchema, value);
    }

    @Override
    public String getName() {
        return assertNotNull(getVertex().getName());
    }
}
