package graphql.analysis;

import graphql.PublicApi;
import graphql.util.TraversalControl;

/**
 * Used by {@link QueryTraversal} to visit the nodes of a Query. It is the same as {@link QueryVisitor} except:
 * <ul>
 * <li>it also allows to influence the traversal control and</li>
 * <li>skip / include directives are evaluated (result is accessible in environment) however regardless of the result
 * node will be visited</li>
 * </ul>
 *
 * <p>
 * How this happens in detail (pre vs post-order for example) is defined by {@link QueryTraversal}.
 */
@PublicApi
public interface QueryVisitorWithControl {

    TraversalControl visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment);

    TraversalControl visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment);

    TraversalControl visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment);

}
