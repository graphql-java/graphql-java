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
        String query = test.getGiven().getQuery();
        try {
            new Parser().parseDocument(query);
            // ok it parsed - but that doesnt mean that was expected
            for (Assertion assertion : assertions) {
                if (assertion.getPasses().isPresent()) {
                    return passed(ctx.getTestName(), query);
                }
                if (assertion.getSyntaxError().isPresent()) {
                    return failed(ctx.getTestName(), query, "The query was expected to have NO syntax errors");
                }
            }
            return failed(ctx.getTestName(), query, "The test has no assertions that make sense in the parse context");
        } catch (ParseCancellationException e) {
            // it failed to parse but that might be ok based on assertions
            for (Assertion assertion : assertions) {
                if (assertion.getPasses().isPresent()) {
                    return failed(ctx.getTestName(), query, "The query was NOT expected to have syntax errors");
                }
                if (assertion.getSyntaxError().isPresent()) {
                    return passed(ctx.getTestName(), query);
                }
            }
            return failed(ctx.getTestName(), query, "The test has no assertions that make sense in the parse context");
        }
    }

}
