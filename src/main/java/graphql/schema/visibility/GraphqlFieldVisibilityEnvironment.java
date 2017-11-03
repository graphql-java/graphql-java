package graphql.schema.visibility;

public class GraphqlFieldVisibilityEnvironment {
    private final Object contextObject;

    private GraphqlFieldVisibilityEnvironment(Object contextObject) {
        this.contextObject = contextObject;
    }

    public static Builder newEnvironment() {
        return new Builder();
    }

    public Object getContextObject() {
        return contextObject;
    }

    public static class Builder {
        private Object contextObject;

        public GraphqlFieldVisibilityEnvironment build() {
            return new GraphqlFieldVisibilityEnvironment(contextObject);
        }

        public Builder setContext(Object contextObject) {
            this.contextObject = contextObject;
            return this;
        }
    }
}
