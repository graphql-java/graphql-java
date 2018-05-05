package graphql.analysis;

import graphql.PublicApi;

@PublicApi
public interface QueryVisitor {

    void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment);

    void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment);

    void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment);

}
