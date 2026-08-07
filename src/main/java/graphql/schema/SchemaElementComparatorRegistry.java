package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;

/**
 * Supplies schema-neutral comparators for schema elements.
 */
@ExperimentalApi
@NullMarked
@FunctionalInterface
public interface SchemaElementComparatorRegistry {

    /**
     * A registry that applies GraphQL's default grouped and name ordering.
     */
    SchemaElementComparatorRegistry DEFAULT_REGISTRY =
            environment -> SchemaElementComparators.sensibleGroupedOrder();

    /**
     * A registry that preserves the current order.
     */
    SchemaElementComparatorRegistry AS_IS_REGISTRY =
            environment -> SchemaElementComparators.asIsOrder();

    /**
     * A registry that orders elements by name.
     */
    SchemaElementComparatorRegistry BY_NAME_REGISTRY =
            environment -> SchemaElementComparators.byNameAsc();

    /**
     * Returns the comparator for the supplied semantic schema context.
     *
     * @param environment the parent and element kinds being sorted
     *
     * @return the comparator to apply
     */
    Comparator<SchemaElement> getComparator(
            SchemaElementComparatorEnvironment environment);
}
