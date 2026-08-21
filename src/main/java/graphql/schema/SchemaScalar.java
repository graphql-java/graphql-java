package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A GraphQL scalar type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaScalar
        extends SchemaNamedType, SchemaInputType, SchemaOutputType {

    /**
     * Returns the coercing associated with this scalar.
     *
     * @return the scalar coercing
     */
    Coercing<?, ?> getCoercing();

    /**
     * Returns the URL identifying the specification implemented by this scalar.
     *
     * @return the URL identifying the scalar specification, or {@code null} when absent
     */
    @Nullable String getSpecifiedByUrl();
}
