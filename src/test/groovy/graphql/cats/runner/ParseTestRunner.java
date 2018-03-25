package graphql.cats.runner;

import graphql.cats.model.Assertion;
import graphql.cats.model.Test;
import graphql.parser.Parser;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.List;

import static graphql.cats.runner.TestResult.failed;
import static graphql.cats.runner.TestResult.passed;

class ParseTestRunner {

    static TestResult runTest(TestContext ctx) {
        Test test = ctx.getTest();
        List<Assertion> assertions = test.getAssertions();
        try {
            String query = test.getGiven().getQuery();
            new Parser().parseDocument(query);
            // ok it parsed - but that doesnt mean that was expected
            for (Assertion assertion : assertions) {
                if (assertion.getPasses().isPresent()) {
                    return passed(ctx.getTestName());
                }
                if (assertion.getSytaxError().isPresent()) {
                    return failed(ctx.getTestName(), "The query was expected to have syntax errors");
                }
            }
            return failed(ctx.getTestName(), "The test has no assertions that make sense in the parse context");
        } catch (ParseCancellationException e) {
            // it failed to parse but that might be ok based on assertions
            for (Assertion assertion : assertions) {
                if (assertion.getPasses().isPresent()) {
                    return failed(ctx.getTestName(), "The query was not expected to have no syntax errors");
                }
                if (assertion.getSytaxError().isPresent()) {
                    return passed(ctx.getTestName());
                }
            }
            return failed(ctx.getTestName(), "The test has no assertions that make sense in the parse context");
        }
    }

}
