package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaInputType;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaIntrospectionArgument implements SchemaArgument {

    private final SUSchemaExecutableSchema executableSchema;
    private final GraphQLArgument argument;

    @Internal
    public SUSchemaIntrospectionArgument(
            SUSchemaExecutableSchema executableSchema,
            GraphQLArgument argument) {
        this.executableSchema = assertNotNull(executableSchema);
        this.argument = assertNotNull(argument);
    }

    @Override
    public String getName() {
        return argument.getName();
    }

    @Override
    public SchemaInputType getType() {
        return executableSchema.adaptGraphQLInputType(argument.getType());
    }

    @Override
    public InputValueWithState getArgumentDefaultValue() {
        return argument.getArgumentDefaultValue();
    }
}
