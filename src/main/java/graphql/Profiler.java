package graphql;

import graphql.schema.DataFetcher;
import org.jspecify.annotations.NullMarked;

@Internal
@NullMarked
public interface Profiler {

    Profiler NO_OP = new Profiler() {
    };

    default void start() {

    }


    default void rootFieldCount(int size) {

    }

    default void subSelectionCount(int size) {

    }

    default void fieldFetched(Object fetchedObject, DataFetcher<?> dataFetcher) {

    }
}
