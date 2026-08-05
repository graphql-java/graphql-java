package graphql.schema;

import graphql.ExperimentalApi;
import graphql.introspection.Introspection.DirectiveLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A GraphQL directive definition.
 */
@ExperimentalApi
@NullMarked
public interface SchemaDirective {

    String getName();

    boolean isRepeatable();

    Set<DirectiveLocation> validLocations();

    List<? extends SchemaArgument> getArguments();

    @Nullable SchemaArgument getArgument(String name);
}
