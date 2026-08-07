package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

@ExperimentalApi
public final class SUAppliedDirective extends SUVertex {

    private final SUAppliedDirectiveArgument[] arguments;

    @Internal
    public SUAppliedDirective(
            int id,
            int nameId,
            String name,
            List<SUAppliedDirectiveArgument> arguments) {
        super(id, nameId, SUVertexKind.APPLIED_DIRECTIVE, name, null);
        this.arguments = assertNotNull(arguments)
                .toArray(new SUAppliedDirectiveArgument[0]);
    }

    public List<SUAppliedDirectiveArgument> getArguments() {
        if (arguments.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(arguments));
    }

    public @Nullable SUAppliedDirectiveArgument getArgument(String name) {
        assertNotNull(name);
        for (SUAppliedDirectiveArgument argument : arguments) {
            if (name.equals(argument.getName())) {
                return argument;
            }
        }
        return null;
    }
}
