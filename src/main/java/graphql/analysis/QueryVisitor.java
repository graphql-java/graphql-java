package graphql.analysis;

import graphql.PublicApi;

/**
 * Used by {@link QueryTraversal} to visit the nodes of a Query.
 * <p>
 * How this happens in detail (pre vs post-order for example) is defined by {@link QueryTraversal}.
 */
@PublicApi
public interface QueryVisitor {

    void visitField(QueryVisitorFieldEnvironment QueryVisitorFieldEnvironment);

    void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment);

    void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment);

}
