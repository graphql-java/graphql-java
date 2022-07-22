package graphql.parser;

import com.google.common.base.Strings;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

/**
 * This is not a test - it's a program we can run to show the system reacts to certain bad inputs
 */
public class BadParserSituations {
    static Integer STEP = 5000;
    static Integer CHECKS_AMOUNT = 15;

    public static void main(String[] args) {
        GraphQL graphQL = setupSchema();

        System.setErr(toDevNull());

        for (int runNumber = 1; runNumber <= 2; runNumber++) {
            String runState = "Limited Tokens";
            // on the second run - have unlimited tokens
            if (runNumber > 1) {
                ParserOptions unlimitedTokens = ParserOptions.getDefaultQueryParserOptions().transform(
                        builder -> builder.maxTokens(Integer.MAX_VALUE));
                ParserOptions.setDefaultQueryParserOptions(unlimitedTokens);

                runState = "Unlimited Tokens";
            }
            runScenarios("Whitespace Bad Payloads", runState, graphQL, howMany -> {
                String repeatedPayload = Strings.repeat("          ", howMany);
                return "query {__typename " + repeatedPayload + " }";
            });
            runScenarios("Comment Bad Payloads", runState, graphQL, howMany -> {
                String repeatedPayload = Strings.repeat("# some comment\n", howMany);
                String query = repeatedPayload + "\nquery q {__typename }";
                return query;
            });
            runScenarios("Grammar Directives Bad Payloads", runState, graphQL, howMany -> {
                String repeatedPayload = Strings.repeat("@lol", howMany);
                return "query {__typename " + repeatedPayload + " }";
            });
            runScenarios("Grammar Field Bad Payloads", runState, graphQL, howMany -> {
                String repeatedPayload = Strings.repeat("f(id:null)", howMany);
                return "query {__typename " + repeatedPayload + " }";
            });

        }

    }

    private static void runScenarios(String scenarioName, String runState, GraphQL graphQL, Function<Integer, String> queryGenerator) {
        long maxRuntime = 0;
        for (int i = 1; i < CHECKS_AMOUNT; i++) {

            int howManyBadPayloads = i * STEP;
            String query = queryGenerator.apply(howManyBadPayloads);

            ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build();
            long startTime = System.nanoTime();

            ExecutionResult executionResult = graphQL.execute(executionInput);

            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);

            System.out.printf("%s(%s)(%d of %d) - | query length %d | bad payloads %d | duration %dms \n", scenarioName, runState, i, CHECKS_AMOUNT, query.length(), howManyBadPayloads, duration.toMillis());
            printLastError(executionResult.getErrors());

            if (duration.toMillis() > maxRuntime) {
                maxRuntime = duration.toMillis();
            }
        }
        System.out.printf("%s(%s) - finished | max time was %s ms \n" +
                "=======================\n\n", scenarioName, runState, maxRuntime);
    }

    private static void printLastError(List<GraphQLError> errors) {
        if (errors.size() > 0) {
            GraphQLError lastError = errors.get(errors.size() - 1);
            System.out.printf("\terror : %s \n", lastError.getMessage());
        }

    }

    private static PrintStream toDevNull() {
        return new PrintStream(new OutputStream() {
            public void write(int b) {
                //DO NOTHING
            }
        });
    }

    private static GraphQL setupSchema() {
        String schema = "type Query{hello: String}";

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("hello", new StaticDataFetcher("world")))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();
        return graphQL;
    }
}
