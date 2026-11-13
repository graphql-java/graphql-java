package graphql.schema.transform;

import graphql.PublicSpi;

/**
 * Predicate used during a {@link FieldVisibilitySchemaTransformation} to test whether an interface implementation
 * relationship should be visible.
 * <p>
 * This predicate controls the relationship only. Field visibility is controlled independently by
 * {@link VisibleFieldPredicate}.
 */
@PublicSpi
@FunctionalInterface
public interface VisibleInterfaceImplementationPredicate {

    /**
     * Tests whether an interface implementation relationship should be visible.
     *
     * @param environment the interface implementation relationship
     *
     * @return true if the relationship should remain visible
     */
    boolean isVisible(VisibleInterfaceImplementationPredicateEnvironment environment);
}
