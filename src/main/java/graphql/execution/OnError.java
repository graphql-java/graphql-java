package graphql.execution;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * Controls how errors are handled during execution
 */
@ExperimentalApi
@NullMarked
public enum OnError {
    NULL,
    PROPAGATE
}
