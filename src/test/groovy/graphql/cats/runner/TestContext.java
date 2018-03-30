package graphql.cats.runner;

import graphql.cats.model.Scenario;
import graphql.cats.model.Test;

public class TestContext {
    private final Scenario scenario;
    private final Test test;
    private final String schema;
    private final Object data;

    private TestContext(Scenario scenario, Test test, String schema, Object data) {
        this.scenario = scenario;
        this.test = test;
        this.schema = schema;
        this.data = data;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public String getTestName() {
        return scenario.getScenario() + " - " + (test == null ? "" : test.getName());
    }

    public Test getTest() {
        return test;
    }

    public String getSchema() {
        return schema;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return getTestName();
    }

    static Builder newContext() {
        return new Builder();
    }

    Builder addTo() {
        return new Builder(this);
    }

    public static class Builder {
        private Scenario scenario;
        private Test test;
        private String schema;
        private Object data;

        public Builder() {
        }

        public Builder(TestContext testContext) {
            this.scenario = testContext.scenario;
            this.test = testContext.test;
            this.schema = testContext.schema;
            this.data = testContext.data;
        }

        public Builder scenario(Scenario scenario) {
            this.scenario = scenario;
            return this;
        }

        public Builder test(Test test) {
            this.test = test;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public TestContext build() {
            return new TestContext(scenario, test, schema, data);
        }
    }

}
