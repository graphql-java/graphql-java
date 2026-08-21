package graphql.schema.universe.view;

import graphql.Internal;
import graphql.language.Node;
import graphql.schema.SchemaAppliedDirective;
import graphql.schema.SchemaElement;
import graphql.schema.universe.SUAppliedDirective;
import graphql.schema.universe.SUVertex;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * Shared identity for schema-bound universe adapters.
 */
@Internal
public abstract class AbstractSUSchemaElement implements SchemaElement {

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

    public final List<? extends SchemaAppliedDirective> getAppliedDirectives() {
        List<SUAppliedDirective> directives =
                executableSchema.getSchema().getAppliedDirectives(vertex);
        List<SchemaAppliedDirective> result =
                new ArrayList<>(directives.size());
        for (SUAppliedDirective directive : directives) {
            result.add(new SUSchemaAppliedDirective(
                    executableSchema,
                    directive));
        }
        return Collections.unmodifiableList(result);
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
