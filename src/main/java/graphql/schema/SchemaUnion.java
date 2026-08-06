package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A GraphQL union type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaUnion extends SchemaComposite {

    /**
     * Returns the types declared as members of this union.
     *
     * <p>Elements obtained from an {@link ExecutableSchema} return
     * {@link SchemaObject} instances.</p>
     *
     * @return the union member types
     */
    List<? extends SchemaNamedType> getTypes();
}
