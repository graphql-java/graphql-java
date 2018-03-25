package graphql.cats.runner;

import graphql.cats.model.Action;
import graphql.cats.model.Scenario;
import graphql.cats.model.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ScenarioTestRunner {

    public static List<TestResult> runScenario(Scenario scenario) {
        TestContext ctx = TestContext.newContext().scenario(scenario).build();

        return scenario.getTests().stream()
                .map(test -> runTest(ctx, test))
                .collect(Collectors.toList());
    }

    private static TestResult runTest(TestContext ctx, Test test) {
        ctx = ctx.addTo().test(test).build();
        try {
            Optional<String> schema = CatsLoader.loadSchema(test.getGiven(), ctx.getScenario().getBackground());
            Optional<Object> data = CatsLoader.loadData(test.getGiven(), ctx.getScenario().getBackground());
            if (schema.isPresent()) {
                ctx = ctx.addTo().schema(schema.get()).build();
            }
            if (data.isPresent()) {
                ctx = ctx.addTo().data(data.get()).build();
            }

            Action action = test.getAction();
            if (action.isParse()) {
                return ParseTestRunner.runTest(ctx);
            } else if (action.getValidate().isPresent()) {
                return ValidationTestRunner.runTest(ctx);
            } else if (action.getExecute().isPresent()) {
                return ExecuteTestRunner.runTest(ctx);
            } else {
                throw new IllegalStateException("Unhandled action!");
            }
        } catch (Exception e) {
            return TestResult.failed(ctx.getTestName(), "Exception while running test", e);
        }
    }

}
