package graphql.schema.universe.view;

import graphql.Internal;
import graphql.schema.universe.SUVertex;

import static graphql.Assert.assertNotNull;

/**
 * Shared identity for schema-bound universe adapters.
 */
@Internal
public abstract class AbstractSUSchemaElement {

    private final SUSchemaExecutableSchema executableSchema;
    private final SUVertex vertex;

    @Internal
    public AbstractSUSchemaElement(
            SUSchemaExecutableSchema executableSchema,
            SUVertex vertex) {
        this.executableSchema = assertNotNull(executableSchema);
        this.vertex = assertNotNull(vertex);
    }

    @Internal
    public final SUSchemaExecutableSchema getExecutableSchema() {
        return executableSchema;
    }

    @Internal
    public final SUVertex getVertex() {
        return vertex;
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
