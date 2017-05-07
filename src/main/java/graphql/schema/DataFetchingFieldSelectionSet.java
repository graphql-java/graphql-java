package graphql.schema;

import graphql.language.Field;

import java.util.List;
import java.util.Map;

/**
 * This allows you to retrieve the selection set of fields that have been asked for when the
 * {@link DataFetcher} was invoked
 */
public interface DataFetchingFieldSelectionSet {

    /**
     * @return a map of the fields that represent the selection set
     */
    Map<String, List<Field>> get();
}
