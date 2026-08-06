package graphql.schema.universe.view;

import graphql.Internal;
import graphql.language.Argument;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaAppliedDirectiveArgument;
import graphql.schema.SchemaInputType;
import graphql.schema.universe.SUAppliedDirectiveArgument;
import graphql.schema.universe.SUType;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaAppliedDirectiveArgument
        implements SchemaAppliedDirectiveArgument {

    private final SUExecutableSchema executableSchema;
    private final SUAppliedDirectiveArgument argument;

    @Internal
    public SUSchemaAppliedDirectiveArgument(
            SUExecutableSchema executableSchema,
            SUAppliedDirectiveArgument argument) {
        this.executableSchema = assertNotNull(executableSchema);
        this.argument = assertNotNull(argument);
    }

    @Override
    public String getName() {
        return argument.getName();
    }

    @Override
    public @Nullable String getDescription() {
        return null;
    }

    @Override
    public @Nullable Argument getDefinition() {
        return argument.getDefinition();
    }

    @Override
    public SchemaInputType getType() {
        SUType type = executableSchema.getSchema().getType(argument);
        return executableSchema.adaptInputType(type);
    }

    @Override
    public InputValueWithState getArgumentValue() {
        return argument.getArgumentValue();
    }
}
