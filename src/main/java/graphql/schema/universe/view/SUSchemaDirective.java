package graphql.schema.universe.view;

import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaDirective;
import graphql.schema.universe.SUArgument;
import graphql.schema.universe.SUDirective;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaDirective
        extends AbstractSUSchemaElement implements SchemaDirective {

    @Internal
    public SUSchemaDirective(
            SUExecutableSchema executableSchema,
            SUDirective directive) {
        super(executableSchema, directive);
    }

    @Override
    public String getName() {
        return assertNotNull(getVertex().getName());
    }

    @Override
    public boolean isRepeatable() {
        return getDirectiveVertex().isRepeatable();
    }

    @Override
    public Set<DirectiveLocation> validLocations() {
        return getDirectiveVertex().validLocations();
    }

    @Override
    public List<SUSchemaArgument> getArguments() {
        List<SUArgument> arguments = getExecutableSchema()
                .getSchema()
                .getArguments(getDirectiveVertex());
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
                .getArgument(getDirectiveVertex(), name);
        if (argument == null) {
            return null;
        }
        return new SUSchemaArgument(getExecutableSchema(), argument);
    }

    @Internal
    public SUDirective getDirectiveVertex() {
        return (SUDirective) getVertex();
    }
}
