package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.language.InputValueDefinition;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@ExperimentalApi
@NullMarked
public final class SUArgument extends SUVertex implements SUAppliedDirectiveContainer {

    private final InputValueWithState defaultValue;
    private final @Nullable InputValueDefinition definition;

    @Internal
    public SUArgument(
            int id,
            int nameId,
            String name,
            @Nullable String description,
            InputValueWithState defaultValue,
            @Nullable InputValueDefinition definition) {
        super(id, nameId, SUVertexKind.ARGUMENT, name, description);
        this.defaultValue = assertNotNull(defaultValue);
        this.definition = definition;
    }

    public InputValueWithState getArgumentDefaultValue() {
        return defaultValue;
    }

    public @Nullable InputValueDefinition getDefinition() {
        return definition;
    }
}
