package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static graphql.Assert.assertNotNull;

/**
 * A schema element in a {@link SchemaUniverse}.
 *
 * <p>Vertices contain only properties intrinsic to the element. Relationships to other schema
 * elements are stored by each {@link SUSchema}.</p>
 */
@ExperimentalApi
@NullMarked
public abstract class SUVertex {

    private final int id;
    private final int nameId;
    private final SUVertexKind kind;
    private final @Nullable String name;
    private final @Nullable String description;

    @Internal
    public SUVertex(
            int id,
            int nameId,
            SUVertexKind kind,
            @Nullable String name,
            @Nullable String description) {
        this.id = id;
        this.nameId = nameId;
        this.kind = assertNotNull(kind);
        this.name = name;
        this.description = description;
    }

    public final int getId() {
        return id;
    }

    public final SUVertexKind getKind() {
        return kind;
    }

    public final @Nullable String getName() {
        return name;
    }

    public final @Nullable String getDescription() {
        return description;
    }

    @Internal
    public final int getNameId() {
        return nameId;
    }

    @Override
    public String toString() {
        return kind + "{" + (name == null ? id : name) + "}";
    }
}
