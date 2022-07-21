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

        for (int runNumber = 1; runNumber <=2; runNumber++) {
            // on the second run - have unlimited tokens
            if (runNumber > 1) {
                ParserOptions unlimitedTokens = ParserOptions.getDefaultParserOptions().transform(builder -> builder.maxTokens(Integer.MAX_VALUE));
                ParserOptions.setDefaultParserOptions(unlimitedTokens);
            }
            runScenarios("Whitespace Bad Payloads",runNumber, Strings.repeat(" ", 10), graphQL);
            runScenarios("Grammar Directives Bad Payloads",runNumber, "@lol", graphQL);
            runScenarios("Grammar Field Bad Payloads",runNumber, "f(id:null)", graphQL);

        }

    }

    private static void runScenarios(String scenarioName, int runNumber, String badPayload, GraphQL graphQL) {
        long maxRuntime = 0;
        for (int i = 1; i < CHECKS_AMOUNT; i++) {

            int howManyBadPayloads = i * STEP;
            String repeatedPayload = Strings.repeat(badPayload, howManyBadPayloads);
            String query = "query {__typename " + repeatedPayload + " }";

            ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build();
            long startTime = System.nanoTime();

            ExecutionResult executionResult = graphQL.execute(executionInput);

            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);

            System.out.printf("%s(run #%d)(%d of %d) - | query length %d | bad payloads %d | duration %dms \n", scenarioName, runNumber, i, CHECKS_AMOUNT, query.length(), howManyBadPayloads, duration.toMillis());
            printLastError(executionResult.getErrors());

            if (duration.toMillis() > maxRuntime) {
                maxRuntime = duration.toMillis();
            }
        }
        System.out.printf("%s(run #%d) - finished | max time was %s ms \n" +
                "=======================\n\n", scenarioName, runNumber, maxRuntime);
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
