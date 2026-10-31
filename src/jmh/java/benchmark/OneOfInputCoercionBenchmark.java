package benchmark;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.execution.RawVariables;
import graphql.execution.ValuesResolver;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.parser.Parser;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class OneOfInputCoercionBenchmark {

    private static final int VALUES_PER_LEVEL = 8;

    @Param({"NONE", "UNRELATED", "REACHABLE"})
    public String oneOfCase;

    @Param({"1", "8"})
    public int depth;

    private TypeDefinitionRegistry registry;
    private RuntimeWiring wiring;
    private GraphQLSchema schema;
    private GraphQL graphQL;
    private ExecutionInput literalInput;
    private ExecutionInput variableInput;
    private List<VariableDefinition> variableDefinitions;
    private RawVariables rawVariables;
    private GraphQLContext graphQLContext;

    @Setup
    public void setup() {
        registry = new SchemaParser().parse(schemaDefinition());
        wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("result", this::countValues))
                .build();
        schema = buildSchema();
        graphQLContext = GraphQLContext.getDefault();
        Map<String, PreparsedDocumentEntry> documents = new LinkedHashMap<>();
        graphQL = GraphQL.newGraphQL(schema).preparsedDocumentProvider((input, parseAndValidate) -> {
            PreparsedDocumentEntry entry = documents.computeIfAbsent(input.getQuery(), ignored -> parseAndValidate.apply(input));
            return CompletableFuture.completedFuture(entry);
        }).build();
        String variableQuery = "query($input: Filter0!) { result(input: $input) }";
        variableDefinitions = Parser.parse(variableQuery).getDefinitionsOfType(OperationDefinition.class).get(0).getVariableDefinitions();
        rawVariables = RawVariables.of(Map.of("input", inputValue()));
        literalInput = ExecutionInput.newExecutionInput().query("{ result(input: " + inputLiteral() + ") }").build();
        variableInput = ExecutionInput.newExecutionInput().query(variableQuery).variables(rawVariables.toMap()).build();
        validateResult(executeLiteralArgument());
        validateResult(executeVariableArgument());
    }

    @Benchmark
    public ExecutionResult executeLiteralArgument() {
        return graphQL.execute(literalInput);
    }

    @Benchmark
    public ExecutionResult executeVariableArgument() {
        return graphQL.execute(variableInput);
    }

    @Benchmark
    public CoercedVariables coerceVariables() {
        return ValuesResolver.coerceVariableValues(schema, variableDefinitions, rawVariables, graphQLContext, Locale.ROOT);
    }

    @Benchmark
    public GraphQLSchema buildSchema() {
        return new SchemaGenerator().makeExecutableSchema(registry, wiring);
    }

    private void validateResult(ExecutionResult result) {
        Assert.assertTrue(result.getErrors().isEmpty(), "Benchmark query failed: %s", result.getErrors());
        Map<String, Object> data = result.getData();
        Assert.assertTrue(Integer.valueOf(VALUES_PER_LEVEL * depth).equals(data.get("result")), "Unexpected benchmark result");
    }

    @SuppressWarnings("unchecked")
    private int countValues(DataFetchingEnvironment environment) {
        Map<String, Object> input = environment.getArgument("input");
        int count = 0;
        while (input != null) {
            count += ((List<?>) input.get("values")).size();
            input = (Map<String, Object>) input.get("next");
        }
        return count;
    }

    private String schemaDefinition() {
        String leafDirective = oneOfCase.equals("REACHABLE") ? "@oneOf" : "";
        String otherDirective = oneOfCase.equals("UNRELATED") ? "@oneOf" : "";
        StringBuilder sdl = new StringBuilder()
                .append("input Leaf ").append(leafDirective).append(" { value: Int, other: Int }\n")
                .append("input Other ").append(otherDirective).append(" { value: Int, other: Int }\n");
        for (int level = 0; level < depth; level++) {
            sdl.append("input Filter").append(level).append(" { values: [Leaf!]!, next: Filter")
                    .append((level + 1) % depth).append(" }\n");
        }
        return sdl.append("type Query { result(input: Filter0!): Int, unused(input: Other): Int }").toString();
    }

    private Map<String, Object> inputValue() {
        List<Map<String, Integer>> values = Collections.nCopies(VALUES_PER_LEVEL, Map.of("value", 1));
        Map<String, Object> input = Map.of("values", values);
        for (int level = 1; level < depth; level++) {
            input = Map.of("values", values, "next", input);
        }
        return input;
    }

    private String inputLiteral() {
        String values = "values: [" + String.join(",", Collections.nCopies(VALUES_PER_LEVEL, "{value: 1}")) + "]";
        String input = "{" + values + "}";
        for (int level = 1; level < depth; level++) {
            input = "{" + values + ", next: " + input + "}";
        }
        return input;
    }
}
