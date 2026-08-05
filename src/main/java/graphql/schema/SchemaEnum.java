package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A GraphQL enum type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaEnum
        extends SchemaNamedType, SchemaInputType, SchemaOutputType {

    List<? extends SchemaEnumValue> getValues();

    @Nullable SchemaEnumValue getValue(String name);
}
