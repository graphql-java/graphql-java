package graphql.schema;

import graphql.language.Field;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * This allows you to retrieve the selection set of fields that have been asked for when the
 * {@link DataFetcher} was invoked.
 *
 * For example imagine we are fetching the field 'user' in the following query
 *
 * <pre>
 * {@code
 *
 *  {
 *      user {
 *          name
 *          age
 *          weight
 *          friends {
 *              name
 *          }
 *      }
 *  }
 * }
 * </pre>
 *
 * The selection set in the case above consists of the fields "name, age, weight, friends and friends/name".
 *
 * You can use this selection set perhaps to "peek" ahead and decide that field values you might need
 * from the underlying data system.  Imagine a SQL system where this might represent the SQL 'projection'
 * of columns say.
 */
public interface DataFetchingFieldSelectionSet extends Supplier<Map<String, List<Field>>> {

    /**
     * @return a map of the fields that represent the selection set
     */
    @Override
    Map<String, List<Field>> get();

    /**
     * @return a map of the arguments for each field in the selection set
     */
    Map<String, Map<String, Object>> getArguments();

    /**
     * @return a map of the {@link graphql.schema.GraphQLFieldDefinition}s for each field in the selection set
     */
    Map<String, GraphQLFieldDefinition> getDefinitions();

    /**
     * This will return true if the field selection set matches a specified "glob" pattern matching ie
     * the glob pattern matching supported by {@link java.nio.file.FileSystem#getPathMatcher}.
     *
     * This will allow you to use '*', '**' and '?' as special matching characters such that "invoice/customer*" would
     * match an invoice field with child fields that start with 'customer'.
     *
     * @param fieldGlobPattern the glob pattern to match fields against
     * @return true if the selection set contains these fields
     *
     * @see java.nio.file.FileSystem#getPathMatcher(String)
     */
    boolean contains(String fieldGlobPattern);

}
