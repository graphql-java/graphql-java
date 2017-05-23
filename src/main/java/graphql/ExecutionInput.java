package graphql;

import java.util.Map;

@PublicApi
public class ExecutionInput {
    private final String requestString;
    private final String operationName;
    private final Object context;
    private final Object root;
    private final Map<String, Object> arguments;


    public ExecutionInput(String requestString, String operationName, Object context, Object root, Map<String, Object> arguments) {
        this.requestString = requestString;
        this.operationName = operationName;
        this.context = context;
        this.root = root;
        this.arguments = arguments;
    }

    public String getRequestString() {
        return requestString;
    }

    public String getOperationName() {
        return operationName;
    }

    public Object getContext() {
        return context;
    }

    public Object getRoot() {
        return root;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ExecutionInput{" +
                "requestString='" + requestString + '\'' +
                ", operationName='" + operationName + '\'' +
                ", context=" + context +
                ", root=" + root +
                ", arguments=" + arguments +
                '}';
    }

    public static Builder newExecutionInput() {
        return new Builder();
    }

    public static class Builder {

        private String requestString;
        private String operationName;
        private Object context;
        private Object root;
        private Map<String, Object> arguments;

        public Builder requestString(String requestString) {
            this.requestString = requestString;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
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

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public ExecutionInput build() {
            return new ExecutionInput(requestString, operationName, context, root, arguments);
        }
    }
}
