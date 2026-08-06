package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A directive applied to a schema element.
 */
@ExperimentalApi
@NullMarked
public interface SchemaAppliedDirective extends SchemaNamedElement {

    /**
     * Returns the arguments supplied to this applied directive.
     *
     * @return the directive arguments in declaration order
     */
    List<? extends SchemaAppliedDirectiveArgument> getArguments();

    /**
     * Returns an argument supplied to this applied directive.
     *
     * @param name the argument name
     *
     * @return the named argument, or {@code null} when absent
     */
    @Nullable SchemaAppliedDirectiveArgument getArgument(String name);
}
