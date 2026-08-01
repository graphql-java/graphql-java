package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.language.Argument;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@ExperimentalApi
@NullMarked
public final class SUAppliedDirectiveArgument {

    private final SchemaUniverse universe;
    private final String name;
    private final int typeId;
    private final InputValueWithState value;
    private final @Nullable Argument definition;

    @Internal
    public SUAppliedDirectiveArgument(
            SchemaUniverse universe,
            String name,
            int typeId,
            InputValueWithState value,
            @Nullable Argument definition) {
        this.universe = assertNotNull(universe);
        this.name = assertNotNull(name);
        this.typeId = typeId;
        this.value = assertNotNull(value);
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public InputValueWithState getArgumentValue() {
        return value;
    }

    public @Nullable Argument getDefinition() {
        return definition;
    }

    @Internal
    public SchemaUniverse getUniverse() {
        return universe;
    }

    @Internal
    public int getTypeId() {
        return typeId;
    }

    @Override
    public String toString() {
        return "APPLIED_DIRECTIVE_ARGUMENT{" + name + "}";
    }
}
