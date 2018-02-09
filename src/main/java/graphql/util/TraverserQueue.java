package graphql.util;

import java.util.List;
import java.util.function.Function;

public class TraverserQueue<T> extends RecursionState<T> {    
    @Override
    public TraverserContext<T> pop() {
        return (TraverserContext<T>) delegate.remove();
    }

    @Override
    public void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren) {
        getChildren.apply(o.thisNode()).iterator().forEachRemaining((e) -> delegate.add(newContext(e, o)));
        delegate.add(TraverserMarkers.END_LIST);
        delegate.add(o);
    }    
}
