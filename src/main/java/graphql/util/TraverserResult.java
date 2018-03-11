package graphql.util;

import graphql.Internal;

@Internal
public class TraverserResult {

    private final boolean encounteredCycle;
    private final Object result;
    private final boolean fullTraversal;


    public TraverserResult(boolean encounteredCycle, Object result, boolean fullTraversal) {
        this.encounteredCycle = encounteredCycle;
        this.result = result;
        this.fullTraversal = fullTraversal;
    }

    public boolean isEncounteredCycle() {
        return encounteredCycle;
    }

    public Object getResult() {
        return result;
    }

    public boolean isFullTraversal() {
        return fullTraversal;
    }
}
