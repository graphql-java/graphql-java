package graphql.schema.universe.view;

import graphql.Internal;
import graphql.language.InputValueDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaInputType;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaIntrospectionArgument implements SchemaArgument {

    private final SUExecutableSchema executableSchema;
    private final GraphQLArgument argument;

    @Internal
    public SUSchemaIntrospectionArgument(
            SUExecutableSchema executableSchema,
            GraphQLArgument argument) {
        this.executableSchema = assertNotNull(executableSchema);
        this.argument = assertNotNull(argument);
    }

    @Override
    public String getName() {
        return argument.getName();
    }

    @Override
    public @Nullable String getDescription() {
        return argument.getDescription();
    }

    @Override
    public @Nullable InputValueDefinition getDefinition() {
        return argument.getDefinition();
    }

    @Override
    public SchemaInputType getType() {
        return executableSchema.adaptGraphQLInputType(argument.getType());
    }

    @Override
    public InputValueWithState getArgumentDefaultValue() {
        return argument.getArgumentDefaultValue();
    }

    @Internal
    public SUExecutableSchema getExecutableSchema() {
        return executableSchema;
    }
}
