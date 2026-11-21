package graphql.normalized;

import graphql.PublicApi;
import graphql.language.DirectivesContainer;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/** A stored field or fragment scope, with its unevaluated source directives. */
@PublicApi
@NullMarked
public sealed interface NormalizedSelection extends Serializable permits NormalizedField, NormalizedSelectionScope {
    /** @return the original AST node, including symbolic skip/include conditions */
    DirectivesContainer<?> getSource();

    /** @return the child selection set ID, or -1 for a leaf */
    int getSelectionSetId();
}
