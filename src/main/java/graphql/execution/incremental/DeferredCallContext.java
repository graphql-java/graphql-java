package graphql.execution.incremental;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.VisibleForTesting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contains data relevant to the execution of a {@link DeferredFragmentCall}.
 * <p>
 * The responsibilities of this class are similar to {@link graphql.execution.ExecutionContext}, but restricted to the
 * execution of a deferred call (instead of the whole GraphQL execution like {@link graphql.execution.ExecutionContext}).
 * <p>
 * Some behaviours, like error capturing, need to be scoped to a single {@link DeferredFragmentCall}, because each defer payload
 * contains its own distinct list of errors.
 */
@Internal
public class DeferredCallContext {

    private final int startLevel;
    private final int fields;

    private final List<GraphQLError> errors = new CopyOnWriteArrayList<>();

    public DeferredCallContext(int startLevel, int fields) {
        this.startLevel = startLevel;
        this.fields = fields;
    }

    @VisibleForTesting
    public DeferredCallContext() {
        this.startLevel = 0;
        this.fields = 0;
    }

    public int getStartLevel() {
        return startLevel;
    }

    public int getFields() {
        return fields;
    }


    public void addErrors(List<GraphQLError> errors) {
        this.errors.addAll(errors);
    }

    public void addError(GraphQLError graphqlError) {
        errors.add(graphqlError);
    }

    /**
     * @return a list of errors that were encountered while executing this deferred call
     */
    public List<GraphQLError> getErrors() {
        return errors;
    }


}
