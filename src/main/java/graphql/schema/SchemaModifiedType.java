package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A type that wraps and modifies another type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaModifiedType extends SchemaType {

    SchemaType getWrappedType();
}
