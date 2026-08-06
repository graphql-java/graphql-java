package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@ExperimentalApi
public final class SUArgument extends SUVertex implements SUAppliedDirectiveContainer {

    private final InputValueWithState defaultValue;

    @Internal
    public SUArgument(
            int id,
            int nameId,
            String name,
            @Nullable String description,
            InputValueWithState defaultValue) {
        super(id, nameId, SUVertexKind.ARGUMENT, name, description);
        this.defaultValue = assertNotNull(defaultValue);
    }

    public InputValueWithState getArgumentDefaultValue() {
        return defaultValue;
    }
}
