package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@ExperimentalApi
public final class SUInputField extends SUVertex implements SUAppliedDirectiveContainer {

    private final InputValueWithState defaultValue;

    @Internal
    public SUInputField(
            int id,
            int nameId,
            String name,
            @Nullable String description,
            InputValueWithState defaultValue) {
        super(id, nameId, SUVertexKind.INPUT_FIELD, name, description);
        this.defaultValue = assertNotNull(defaultValue);
    }

    public InputValueWithState getInputFieldDefaultValue() {
        return defaultValue;
    }
}
