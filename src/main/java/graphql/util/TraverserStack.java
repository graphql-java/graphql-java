package graphql.util;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TraverserStack<T> extends RecursionState<T> {
    @Override
    public TraverserContext<T> pop() {
        return (TraverserContext<T>) delegate.pop();
    }

    @Override
    public void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren) {
        delegate.push(o);
        delegate.push(TraverserMarkers.END_LIST);
        getChildren.apply(o.thisNode()).stream().collect(Collectors.toCollection(ArrayDeque::new)).descendingIterator().forEachRemaining((e) -> delegate.push(newContext(e, o)));
    }
}
