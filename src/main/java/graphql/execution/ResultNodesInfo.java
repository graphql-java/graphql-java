package graphql.execution;

import graphql.Internal;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is used to track the number of result nodes that have been created during execution.
 * After each execution the GraphQLContext contains a ResultNodeInfo object under the key {@link ResultNodesInfo#RESULT_NODES_INFO}
 * <p>
 * The number of result can be limited (and should be for security reasons) by setting the maximum number of result nodes
 * in the GraphQLContext under the key {@link ResultNodesInfo#MAX_RESULT_NODES} to an Integer
 * </p>
 */
@PublicApi
@NullMarked
public class ResultNodesInfo {

    public static final String MAX_RESULT_NODES = "__MAX_RESULT_NODES";
    public static final String RESULT_NODES_INFO = "__RESULT_NODES_INFO";

    private volatile boolean maxResultNodesExceeded = false;
    private final AtomicInteger resultNodesCount = new AtomicInteger(0);

    @Internal
    public int incrementAndGetResultNodesCount() {
        return resultNodesCount.incrementAndGet();
    }

    @Internal
    public void maxResultNodesExceeded() {
        this.maxResultNodesExceeded = true;
    }

    /**
     * The number of result nodes created.
     * Note: this can be higher than max result nodes because
     * a each node that exceeds the number of max nodes is set to null,
     * but still is a result node (with value null)
     *
     * @return number of result nodes created
     */
    public int getResultNodesCount() {
        return resultNodesCount.get();
    }

    /**
     * If the number of result nodes has exceeded the maximum allowed numbers.
     *
     * @return true if the number of result nodes has exceeded the maximum allowed numbers
     */
    public boolean isMaxResultNodesExceeded() {
        return maxResultNodesExceeded;
    }
}
