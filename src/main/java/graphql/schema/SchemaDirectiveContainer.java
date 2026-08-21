package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A schema element on which directives can be applied.
 */
@ExperimentalApi
@NullMarked
public interface SchemaDirectiveContainer extends SchemaNamedElement {

    /**
     * Returns the directives applied directly to this element.
     *
     * @return applied directives in declaration order
     */
    List<? extends SchemaAppliedDirective> getAppliedDirectives();
}
