package graphql.analysis;

import graphql.Internal;
import graphql.util.TraversalControl;

@Internal
class QueryVisitorAdapter implements QueryVisitorWithControl {
    private final QueryVisitor queryVisitor;

    QueryVisitorAdapter(QueryVisitor queryVisitor) {
        this.queryVisitor = queryVisitor;
    }

    @Override
    public TraversalControl visitField(QueryVisitorFieldEnvironment environment) {
        if (!environment.shouldInclude()) {
            return TraversalControl.ABORT;
        }
        queryVisitor.visitField(environment);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {
        if (!environment.shouldInclude()) {
            return TraversalControl.ABORT;
        }
        queryVisitor.visitInlineFragment(environment);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {
        if (!environment.shouldInclude()) {
            return TraversalControl.ABORT;
        }
        queryVisitor.visitFragmentSpread(environment);
        return TraversalControl.CONTINUE;
    }
}
