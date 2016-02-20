package graphql;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>ExecutionResultImpl class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class ExecutionResultImpl implements ExecutionResult {

    private final List<GraphQLError> errors = new ArrayList<GraphQLError>();
    private Object data;

    /**
     * <p>Constructor for ExecutionResultImpl.</p>
     *
     * @param errors a {@link java.util.List} object.
     */
    public ExecutionResultImpl(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    /**
     * <p>Constructor for ExecutionResultImpl.</p>
     *
     * @param data a {@link java.lang.Object} object.
     * @param errors a {@link java.util.List} object.
     */
    public ExecutionResultImpl(Object data, List<? extends GraphQLError> errors) {
        this.data = data;

        if (errors != null) {
            this.errors.addAll(errors);
        }
    }

    /**
     * <p>addErrors.</p>
     *
     * @param errors a {@link java.util.List} object.
     */
    public void addErrors(List<? extends GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    /** {@inheritDoc} */
    @Override
    public Object getData() {
        return data;
    }

    /**
     * <p>Setter for the field <code>data</code>.</p>
     *
     * @param result a {@link java.lang.Object} object.
     */
    public void setData(Object result) {
        this.data = result;
    }

    /** {@inheritDoc} */
    @Override
    public List<GraphQLError> getErrors() {
        return new ArrayList<GraphQLError>(errors);
    }


}
