package graphql.schema;

import graphql.Internal;

@Internal
public final class LookaheadFieldBudget {
    private final int limit;
    private long fields;

    public LookaheadFieldBudget(int limit) {
        this.limit = limit;
    }

    public void consume(int count) {
        fields += count;
        if (limit != Integer.MAX_VALUE && fields > limit) {
            throw new IllegalStateException("Lookahead field enumeration exceeds QueryComplexityLimits.maxFieldOccurrences (limit=" + limit + ")");
        }
    }
}
