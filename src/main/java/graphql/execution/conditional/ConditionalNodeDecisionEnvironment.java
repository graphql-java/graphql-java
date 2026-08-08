package graphql.execution.conditional;

import graphql.ExperimentalApi;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.schema.ExecutableSchema;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The parameters given to a {@link ConditionalNodeDecision}
 */
@ExperimentalApi
@NullMarked
public interface ConditionalNodeDecisionEnvironment {

    /**
     * This is an AST {@link graphql.language.Node} that has directives on it.
     * {@link graphql.language.Field}, @{@link graphql.language.FragmentSpread} and
     * {@link graphql.language.InlineFragment} are examples of nodes
     * that can be conditionally included.
     *
     * @return the AST element in question
     */
    DirectivesContainer<?> getDirectivesContainer();

    /**
     * @return the list of directives associated with the {@link #getDirectivesContainer()}
     */
    default List<Directive> getDirectives() {
        return getDirectivesContainer().getDirectives();
    }

    /**
     * @return a map of the current variables
     */
    CoercedVariables getVariables();

    /**
     * @return the executable schema view in question
     */
    ExecutableSchema getSchema();

    /**
     * @return a graphql context
     */
    GraphQLContext getGraphQLContext();
}
