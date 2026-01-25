package graphql.execution.conditional;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * This callback interface allows custom implementations to decide if a field is included in a query or not.
 * <p>
 * The default `@skip / @include` is built in, but you can create your own implementations to allow you to make
 * decisions on whether fields are considered part of a query.
 */
@ExperimentalApi
@NullMarked
public interface ConditionalNodeDecision {

    /**
     * This is called to decide if a {@link graphql.language.Node} should be included or not
     *
     * @param decisionEnv ghe environment you can use to make the decision
     *
     * @return true if the node should be included or false if it should be excluded
     */
    boolean shouldInclude(ConditionalNodeDecisionEnvironment decisionEnv);
}

