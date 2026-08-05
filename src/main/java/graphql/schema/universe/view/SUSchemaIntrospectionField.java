package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaField;
import graphql.schema.SchemaOutputType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaIntrospectionField implements SchemaField {

    private final SUSchemaExecutableSchema executableSchema;
    private final GraphQLFieldDefinition field;
    private final @Nullable SchemaOutputType type;

    @Internal
    public SUSchemaIntrospectionField(
            SUSchemaExecutableSchema executableSchema,
            GraphQLFieldDefinition field) {
        this(executableSchema, field, null);
    }

    @Internal
    public SUSchemaIntrospectionField(
            SUSchemaExecutableSchema executableSchema,
            GraphQLFieldDefinition field,
            @Nullable SchemaOutputType type) {
        this.executableSchema = assertNotNull(executableSchema);
        this.field = assertNotNull(field);
        this.type = type;
    }

    @Override
    public String getName() {
        return field.getName();
    }

    @Override
    public SchemaOutputType getType() {
        if (type != null) {
            return type;
        }
        return executableSchema.adaptGraphQLOutputType(field.getType());
    }

    @Override
    public List<SUSchemaIntrospectionArgument> getArguments() {
        List<SUSchemaIntrospectionArgument> result =
                new ArrayList<>(field.getArguments().size());
        for (GraphQLArgument argument : field.getArguments()) {
            result.add(new SUSchemaIntrospectionArgument(
                    executableSchema,
                    argument));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable SchemaArgument getArgument(String name) {
        GraphQLArgument argument = field.getArgument(name);
        if (argument == null) {
            return null;
        }
        return new SUSchemaIntrospectionArgument(
                executableSchema,
                argument);
    }
}
