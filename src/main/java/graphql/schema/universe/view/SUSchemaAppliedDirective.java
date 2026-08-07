package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.SchemaAppliedDirective;
import graphql.schema.SchemaAppliedDirectiveArgument;
import graphql.schema.universe.SUAppliedDirective;
import graphql.schema.universe.SUAppliedDirectiveArgument;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

@Internal
public final class SUSchemaAppliedDirective
        extends AbstractSUSchemaElement implements SchemaAppliedDirective {

    @Internal
    public SUSchemaAppliedDirective(
            SUExecutableSchema executableSchema,
            SUAppliedDirective directive) {
        super(executableSchema, directive);
    }

    @Override
    public String getName() {
        return assertNotNull(getVertex().getName());
    }

    @Override
    public List<SUSchemaAppliedDirectiveArgument> getArguments() {
        List<SUAppliedDirectiveArgument> arguments =
                getDirectiveVertex().getArguments();
        List<SUSchemaAppliedDirectiveArgument> result =
                new ArrayList<>(arguments.size());
        for (SUAppliedDirectiveArgument argument : arguments) {
            result.add(new SUSchemaAppliedDirectiveArgument(
                    getExecutableSchema(),
                    argument));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable SchemaAppliedDirectiveArgument getArgument(String name) {
        SUAppliedDirectiveArgument argument =
                getDirectiveVertex().getArgument(assertNotNull(name));
        if (argument == null) {
            return null;
        }
        return new SUSchemaAppliedDirectiveArgument(
                getExecutableSchema(),
                argument);
    }

    @Internal
    public SUAppliedDirective getDirectiveVertex() {
        return (SUAppliedDirective) getVertex();
    }
}
