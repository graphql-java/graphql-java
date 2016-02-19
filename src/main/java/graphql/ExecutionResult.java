package graphql;


import java.util.List;
import java.util.Map;

/**
 * <p>ExecutionResult interface.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public interface ExecutionResult {

    /**
     * <p>getData.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    Object getData();

    /**
     * <p>getErrors.</p>
     *
     * @return a {@link java.util.List} object.
     */
    List<GraphQLError> getErrors();
}
