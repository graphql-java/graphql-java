package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.language.InputValueDefinition;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

@ExperimentalApi
public final class SUInputField extends SUVertex implements SUAppliedDirectiveContainer {

    private final InputValueWithState defaultValue;
    private final @Nullable InputValueDefinition definition;

    @Internal
    public SUInputField(
            int id,
            int nameId,
            String name,
            @Nullable String description,
            InputValueWithState defaultValue,
            @Nullable InputValueDefinition definition) {
        super(id, nameId, SUVertexKind.INPUT_FIELD, name, description);
        this.defaultValue = assertNotNull(defaultValue);
        this.definition = definition;
    }

    public InputValueWithState getInputFieldDefaultValue() {
        return defaultValue;
    }

    public @Nullable InputValueDefinition getDefinition() {
        return definition;
    }
}
