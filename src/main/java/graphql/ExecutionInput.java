package graphql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This represents the series of values that can be input on a graphql query execution
 */
@PublicApi
public class ExecutionInput {
    private final String query;
    private final List<String> operationNames;
    private final Object context;
    private final Object root;
    private final Map<String, Object> variables;


    public ExecutionInput(String query, String operationName, Object context, Object root, Map<String, Object> variables) {
        this(query, Collections.singletonList(operationName), context, root, variables);
    }

    public ExecutionInput(String query, List<String> operationNames, Object context, Object root, Map<String, Object> variables) {
        this.query = query;
        this.operationNames = operationNames == null ? Collections.emptyList() : operationNames;
        this.context = context;
        this.root = root;
        this.variables = variables;
    }

    /**
     * @return the query text
     */
    public String getQuery() {
        return query;
    }

    /**
     * @return the name of the query operation
     */
    public String getOperationName() {
        if (operationNames == null || operationNames.isEmpty()) {
            return null;
        }
        return operationNames.get(0);
    }

    /**
     * @return the names of the query operations
     */
    public List<String> getOperationNames() {
        return operationNames;
    }

    /**
     * @return the context object to pass to all data fetchers
     */
    public Object getContext() {
        return context;
    }

    /**
     * @return the root object to start the query execution on
     */
    public Object getRoot() {
        return root;
    }

    /**
     * @return a map of variables that can be referenced via $syntax in the query
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * This helps you transform the current ExecutionInput object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new ExecutionInput object based on calling build on that builder
     */
    public ExecutionInput transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder()
                .query(this.query)
                .operationNames(this.operationNames)
                .context(this.context)
                .root(this.root)
                .variables(this.variables);

        builderConsumer.accept(builder);

        return builder.build();
    }


    @Override
    public String toString() {
        return "ExecutionInput{" +
                "query='" + query + '\'' +
                ", operationName='" + getOperationName() + '\'' +
                ", context=" + context +
                ", root=" + root +
                ", variables=" + variables +
                '}';
    }

    /**
     * @return a new builder of ExecutionInput objects
     */
    public static Builder newExecutionInput() {
        return new Builder();
    }

    public static class Builder {

        private String query;
        private List<String> operationNames = new ArrayList<>();
        private Object context;
        private Object root;
        private Map<String, Object> variables = Collections.emptyMap();

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationNames.clear();
            this.operationNames.add(operationName);
            return this;
        }

        public Builder operationNames(List<String> operationNames) {
            this.operationNames.clear();
            this.operationNames.addAll(operationNames);
            return this;
        }

        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder root(Object root) {
            this.root = root;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public ExecutionInput build() {
            return new ExecutionInput(query, operationNames, context, root, variables);
        }
    }
}
