package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A field declared by an object or interface type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaField {

    String getName();

    SchemaOutputType getType();

    List<? extends SchemaArgument> getArguments();

    @Nullable SchemaArgument getArgument(String name);
}
