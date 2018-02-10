package graphql.util;

import java.util.List;
import java.util.function.Function;

public class TraverserQueue<T> extends RecursionState<T> {
    @Override
    public TraverserContext<T> pop() {
        return (TraverserContext<T>) getDelegate().remove();
    }

    @Override
    public void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren) {
        getChildren.apply(o.thisNode()).iterator().forEachRemaining((e) -> getDelegate().add(newContext(e, o)));
        getDelegate().add(TraverserMarkers.END_LIST);
        getDelegate().add(o);
    }
}
