package graphql.execution;

import graphql.ExperimentalApi;
import graphql.PublicSpi;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * Allows to customize the concrete class {@link Map} implementation. For example, it could be possible to use
 * memory-efficient implementations, like eclipse-collections.
 */
@ExperimentalApi
@PublicSpi
@NullMarked
public interface ResponseMapFactory {

    /**
     * The default implementation uses JDK's {@link java.util.LinkedHashMap}.
     */
    ResponseMapFactory DEFAULT = new DefaultResponseMapFactory();

    /**
     * The general contract is that the resulting map keeps the insertion orders of keys. Values are nullable but keys are not.
     * Implementations are free to create or to reuse map instances.
     *
     * @param keys the keys like k1, k2, ..., kn
     * @param values the values  like v1, v2, ..., vn
     * @return a new or reused map instance with (k1,v1), (k2, v2), ... (kn, vn)
     */
    Map<String, Object> createInsertionOrdered(List<String> keys, List<Object> values);

}
