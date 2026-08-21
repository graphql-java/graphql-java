package graphql.schema.universe;

import graphql.Internal;
import graphql.language.Node;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * AST provenance for a vertex that has extension definitions.
 */
@Internal
public final class SUAstDefinitions {

    private final @Nullable Node<?> definition;
    private final List<? extends Node<?>> extensionDefinitions;

    @Internal
    public SUAstDefinitions(
            @Nullable Node<?> definition,
            List<? extends Node<?>> extensionDefinitions) {
        this.definition = definition;
        this.extensionDefinitions = List.copyOf(
                assertNotNull(extensionDefinitions));
    }

    public @Nullable Node<?> getDefinition() {
        return definition;
    }

    public List<? extends Node<?>> getExtensionDefinitions() {
        return extensionDefinitions;
    }
}
