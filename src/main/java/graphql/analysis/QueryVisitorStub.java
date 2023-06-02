package graphql.analysis;

import graphql.PublicApi;

@PublicApi
public class QueryVisitorStub implements QueryVisitor {


    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {

    }

    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {

    }
}
