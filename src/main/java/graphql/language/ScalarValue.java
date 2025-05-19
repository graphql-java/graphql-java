package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public interface ScalarValue<T extends Value> extends Value<T> {
}
