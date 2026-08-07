package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaField;
import graphql.schema.SchemaOutputType;
import graphql.schema.universe.SUArgument;
import graphql.schema.universe.SUField;
import graphql.schema.universe.SUType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaField
        extends AbstractSUSchemaElement implements SchemaField {

    @Internal
    public SUSchemaField(
            SUExecutableSchema executableSchema,
            SUField field) {
        super(executableSchema, field);
    }

    @Override
    public String getName() {
        return assertNotNull(getVertex().getName());
    }

    @Override
    public SchemaOutputType getType() {
        SUType type = assertNotNull(
                getExecutableSchema()
                        .getSchema()
                        .getType(getFieldVertex()));
        return getExecutableSchema().adaptOutputType(type);
    }

    @Override
    public List<SUSchemaArgument> getArguments() {
        List<SUArgument> arguments = getExecutableSchema()
                .getSchema()
                .getArguments(getFieldVertex());
        List<SUSchemaArgument> result =
                new ArrayList<>(arguments.size());
        for (SUArgument argument : arguments) {
            result.add(new SUSchemaArgument(getExecutableSchema(), argument));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable SchemaArgument getArgument(String name) {
        SUArgument argument = getExecutableSchema()
                .getSchema()
                .getArgument(getFieldVertex(), name);
        if (argument == null) {
            return null;
        }
        return new SUSchemaArgument(getExecutableSchema(), argument);
    }

    @Internal
    public SUField getFieldVertex() {
        return (SUField) getVertex();
    }
}
