package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaInputType;
import graphql.schema.universe.SUArgument;
import graphql.schema.universe.SUType;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaArgument
        extends AbstractSUSchemaElement implements SchemaArgument {

    @Internal
    public SUSchemaArgument(
            SUSchemaExecutableSchema executableSchema,
            SUArgument argument) {
        super(executableSchema, argument);
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
                        .getType(getArgumentVertex()));
        return getExecutableSchema().adaptInputType(type);
    }

    @Override
    public InputValueWithState getArgumentDefaultValue() {
        return getArgumentVertex().getArgumentDefaultValue();
    }

    @Internal
    public SUArgument getArgumentVertex() {
        return (SUArgument) getVertex();
    }
}
