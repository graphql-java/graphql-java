package benchmark;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.execution.RawVariables;
import graphql.execution.ValuesResolver;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Coerces a variable made of nested input objects, `depth` levels deep, each level having
 * {@link #FIELDS_PER_LEVEL} optional scalar fields of which only a few are provided. The innermost
 * level holds a `Leaf` input object which is either a plain input object or a `@oneOf` one.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class OneOfVariableCoercionBenchmark {

    private static final int FIELDS_PER_LEVEL = 8;
    private static final int PROVIDED_FIELDS_PER_LEVEL = 3;
    private static final int LIST_SIZE = 20;

    @Param({"1", "8"})
    public int depth;

    @Param({"false", "true"})
    public boolean oneOfLeaf;

    private GraphQLSchema schema;
    private List<VariableDefinition> variableDefinitions;
    private RawVariables singleObjectVariables;
    private RawVariables listOfObjectsVariables;
    private final GraphQLContext graphQLContext = GraphQLContext.getDefault();

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema(sdl());
        String query = "query ($input: Level0, $inputs: [Level0!]) { single(input: $input) list(inputs: $inputs) }";
        variableDefinitions = Parser.parse(query)
                .getFirstDefinitionOfType(OperationDefinition.class).orElseThrow()
                .getVariableDefinitions();

        Map<String, Object> input = inputValue();
        List<Object> inputs = new ArrayList<>();
        for (int i = 0; i < LIST_SIZE; i++) {
            inputs.add(inputValue());
        }
        singleObjectVariables = RawVariables.of(Map.of("input", input));
        listOfObjectsVariables = RawVariables.of(Map.of("inputs", inputs));

        // make sure the variables are valid
        singleObject();
        listOfObjects();
    }

    @Benchmark
    public CoercedVariables singleObject() {
        return ValuesResolver.coerceVariableValues(schema, variableDefinitions, singleObjectVariables, graphQLContext, Locale.ENGLISH);
    }

    @Benchmark
    public CoercedVariables listOfObjects() {
        return ValuesResolver.coerceVariableValues(schema, variableDefinitions, listOfObjectsVariables, graphQLContext, Locale.ENGLISH);
    }

    private String sdl() {
        StringBuilder sdl = new StringBuilder();
        sdl.append("input Leaf ").append(oneOfLeaf ? "@oneOf " : "").append("{ a: Int, b: String }\n");
        for (int level = 0; level < depth; level++) {
            sdl.append("input Level").append(level).append(" {\n");
            for (int f = 0; f < FIELDS_PER_LEVEL; f++) {
                sdl.append("  f").append(f).append(": String\n");
            }
            if (level == depth - 1) {
                sdl.append("  leaf: Leaf\n");
            } else {
                sdl.append("  next: Level").append(level + 1).append("\n");
            }
            sdl.append("}\n");
        }
        sdl.append("type Query { single(input: Level0): Boolean list(inputs: [Level0!]): Boolean }\n");
        return sdl.toString();
    }

    private Map<String, Object> inputValue() {
        Map<String, Object> value = Map.of("a", 1);
        String childFieldName = "leaf";
        for (int level = depth - 1; level >= 0; level--) {
            Map<String, Object> levelValue = new LinkedHashMap<>();
            for (int f = 0; f < PROVIDED_FIELDS_PER_LEVEL; f++) {
                levelValue.put("f" + f, "value" + f);
            }
            levelValue.put(childFieldName, value);
            value = levelValue;
            childFieldName = "next";
        }
        return value;
    }
}
