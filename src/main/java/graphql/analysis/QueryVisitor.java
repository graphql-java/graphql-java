package graphql.analysis;

import graphql.PublicApi;
import graphql.util.TraversalControl;

/**
 * Used by {@link QueryTraverser} to visit the nodes of a Query.
 * <p>
 * How this happens in detail (pre vs post-order for example) is defined by {@link QueryTraverser}.
 */
@PublicApi
public interface QueryVisitor {

    void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment);

    /**
     * visitField variant which lets you control the traversal.
     * default implementation calls visitField for backwards compatibility reason
     *
     * @param environment the environment in play
     * @return traversal control
     */
    default TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment environment) {
        visitField(environment);
        return TraversalControl.CONTINUE;
    }

    void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment);

    void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment);

    default void visitFragmentDefinition(QueryVisitorFragmentDefinitionEnvironment environment) {

    }

    default TraversalControl visitArgument(QueryVisitorFieldArgumentEnvironment environment) {
        return TraversalControl.CONTINUE;
    }

    default TraversalControl visitArgumentValue(QueryVisitorFieldArgumentValueEnvironment environment) {
        return TraversalControl.CONTINUE;
    }
}
