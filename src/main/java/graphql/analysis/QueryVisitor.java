package graphql.analysis;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import org.jspecify.annotations.NullMarked;

/**
 * Used by {@link QueryTraverser} to visit the nodes of a Query.
 * <p>
 * How this happens in detail (pre vs post-order for example) is defined by {@link QueryTraverser}.
 */
@PublicApi
@NullMarked
public interface QueryVisitor {

    void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment);

    /**
     * visitField variant which lets you control the traversal.
     * default implementation calls visitField for backwards compatibility reason
     *
     * @param queryVisitorFieldEnvironment the environment in play
     * @return traversal control
     */
    default TraversalControl visitFieldWithControl(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
        visitField(queryVisitorFieldEnvironment);
        return TraversalControl.CONTINUE;
    }

    void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment);

    void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment);

    default void visitFragmentDefinition(QueryVisitorFragmentDefinitionEnvironment queryVisitorFragmentDefinitionEnvironment) {

    }

    default TraversalControl visitArgument(QueryVisitorFieldArgumentEnvironment environment) {
        return TraversalControl.CONTINUE;
    }

    default TraversalControl visitArgumentValue(QueryVisitorFieldArgumentValueEnvironment environment) {
        return TraversalControl.CONTINUE;
    }
}
