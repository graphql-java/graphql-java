package graphql;


import java.util.List;
import java.util.Map;

/**
 * This simple value class represents the result of performing a graphql query.
 */
@PublicApi
@SuppressWarnings("TypeParameterUnusedInFormals")
public interface ExecutionResult {

    /**
     * @param <T> allows type coercion
     *
     * @return the data in the result or null if there is none
     */
    <T> T getData();

    /**
     * @return the errors that occurred during execution or empty list if there is none
     */
    List<GraphQLError> getErrors();

    /**
     * @return a map of extensions or null if there are none
     */
    Map<Object, Object> getExtensions();


    /**
     * The graphql specification says that result of a call should be a map that follows certain rules on what items
     * should be present.  Certain JSON serializers may or may interpret {@link ExecutionResult} to spec, so this method
     * is provided to produce a map that strictly follows the specification.
     *
     * See : <a href="http://facebook.github.io/graphql/#sec-Response-Format">http://facebook.github.io/graphql/#sec-Response-Format</a>
     *
     * @return a map of the result that strictly follows the spec
     */
    Map<String, Object> toSpecification();
}
