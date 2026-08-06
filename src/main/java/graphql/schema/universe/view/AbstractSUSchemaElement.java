package graphql.schema.universe.view;

import graphql.Internal;
import graphql.language.Node;
import graphql.schema.universe.SUVertex;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

/**
 * Shared identity for schema-bound universe adapters.
 */
@Internal
public abstract class AbstractSUSchemaElement {

    private final SUExecutableSchema executableSchema;
    private final SUVertex vertex;

    @Internal
    public AbstractSUSchemaElement(
            SUExecutableSchema executableSchema,
            SUVertex vertex) {
        this.executableSchema = assertNotNull(executableSchema);
        this.vertex = assertNotNull(vertex);
    }

    @Internal
    public final SUExecutableSchema getExecutableSchema() {
        return executableSchema;
    }

    @Internal
    public final SUVertex getVertex() {
        return vertex;
    }

    /**
     * Returns the description stored on the schema-universe vertex.
     *
     * @return the description, or {@code null} when absent
     */
    public final @Nullable String getDescription() {
        return vertex.getDescription();
    }

    /**
     * Returns the source definition stored in the universe AST sidecar.
     *
     * @return the source definition, or {@code null} when unavailable
     */
    public @Nullable Node<?> getDefinition() {
        return executableSchema.getSchema().getDefinition(vertex);
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        AbstractSUSchemaElement that = (AbstractSUSchemaElement) other;
        return executableSchema.getSchema() == that.executableSchema.getSchema()
                && vertex == that.vertex;
    }

    @Override
    public final int hashCode() {
        return 31 * System.identityHashCode(executableSchema.getSchema())
                + vertex.getId();
    }

    @Override
    public String toString() {
        return vertex.toString();
    }
}
