package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * Visits one source binding in a {@link PersistentEdgeMap}.
 */
@Internal
@NullMarked
@FunctionalInterface
public interface EdgeMapEntryVisitor {

    /**
     * Visits the complete outgoing edge set stored for one source vertex.
     *
     * @param sourceId the source vertex ID
     * @param edges the complete non-empty outgoing edge set
     */
    void visit(int sourceId, PackedEdgeSet edges);
}
