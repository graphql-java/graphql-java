package graphql.execution;

import graphql.PublicSpi;

import java.util.List;
import java.util.Map;

/**
 * Allows to customize the concrete class {@link Map} implementation. For example, it could be possible to use
 * memory-efficient implementations, like eclipse-collections.
 */
@PublicSpi
public interface ResponseMapFactory {

    /**
     * The default implementation uses JDK's {@link java.util.LinkedHashMap}.
     */
    ResponseMapFactory DEFAULT = new DefaultResponseMapFactory();

    Map<String, Object> create(List<String> fieldNames, List<Object> results);

}
