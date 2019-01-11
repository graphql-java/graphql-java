package graphql.analysis;

import graphql.PublicApi;
import graphql.util.TraversalControl;


@PublicApi
public class QueryVisitorWithControlStub implements QueryVisitorWithControl{

    @Override
    public TraversalControl visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {
        return TraversalControl.CONTINUE;
    }
}
