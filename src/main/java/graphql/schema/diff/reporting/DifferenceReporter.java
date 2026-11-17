package graphql.schema.diff.reporting;

import graphql.PublicSpi;
import graphql.schema.diff.DiffEvent;
import org.jspecify.annotations.NullMarked;

/**
 * This is called with each different encountered (including info ones) by a {@link graphql.schema.diff.SchemaDiff} operation
 */
@NullMarked
@PublicSpi
public interface DifferenceReporter {

    /**
     * Called to report a difference
     *
     * @param differenceEvent the event describing the difference
     */
    void report(DiffEvent differenceEvent);

    /**
     * Called when the difference operation if finished
     */
    void onEnd();
}
