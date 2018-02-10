package graphql.util;

import graphql.Internal;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Internal
public class TraverserStack<T> extends RecursionState<T> {
    @Override
    public TraverserContext<T> pop() {
        return (TraverserContext<T>) getDelegate().pop();
    }

    @Override
    public void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren) {
        getDelegate().push(o);
        getDelegate().push(TraverserMarkers.END_LIST);
        getChildren.apply(o.thisNode()).stream().collect(Collectors.toCollection(ArrayDeque::new)).descendingIterator().forEachRemaining((e) -> getDelegate().push(newContext(e, o)));
    }
}
