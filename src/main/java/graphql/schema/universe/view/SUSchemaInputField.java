package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputType;
import graphql.schema.universe.SUInputField;
import graphql.schema.universe.SUType;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaInputField
        extends AbstractSUSchemaElement implements SchemaInputField {

    @Internal
    public SUSchemaInputField(
            SUSchemaExecutableSchema executableSchema,
            SUInputField field) {
        super(executableSchema, field);
    }

    @Override
    public String getName() {
        return assertNotNull(getVertex().getName());
    }

    @Override
    public SchemaInputType getType() {
        SUType type = assertNotNull(
                getExecutableSchema()
                        .getSchema()
                        .getType(getInputFieldVertex()));
        return getExecutableSchema().adaptInputType(type);
    }

    @Override
    public InputValueWithState getInputFieldDefaultValue() {
        return getInputFieldVertex().getInputFieldDefaultValue();
    }

    @Internal
    public SUInputField getInputFieldVertex() {
        return (SUInputField) getVertex();
    }
}
