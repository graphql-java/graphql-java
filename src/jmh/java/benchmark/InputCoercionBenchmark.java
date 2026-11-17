package benchmark;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.execution.RawVariables;
import graphql.execution.ValuesResolver;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(2)
public class InputCoercionBenchmark {

    @Param({"scalar", "shallowObject", "flatList", "nestedObject"})
    public String scenario;

    private GraphQLSchema schema;
    private List<VariableDefinition> definitions;
    private RawVariables variables;
    private GraphQLContext context;

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema("type Query { echo(arg: Recursive): Int } " +
                "input Recursive { next: Recursive items: [Int] }");
        boolean scalar = "scalar".equals(scenario);
        definitions = Collections.singletonList(new VariableDefinition("v", new TypeName(scalar ? "Int" : "Recursive")));
        variables = RawVariables.of(Collections.singletonMap("v", inputValue()));
        context = GraphQLContext.getDefault();
    }

    @Benchmark
    public CoercedVariables coerce() {
        return ValuesResolver.coerceVariableValues(schema, definitions, variables, context, Locale.ROOT);
    }

    private Object inputValue() {
        if ("scalar".equals(scenario)) {
            return 42;
        }
        if ("shallowObject".equals(scenario)) {
            return Collections.emptyMap();
        }
        if ("flatList".equals(scenario)) {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                items.add(i);
            }
            return Collections.singletonMap("items", items);
        }
        Map<String, Object> value = new LinkedHashMap<>();
        for (int i = 0; i < 32; i++) {
            value = Collections.singletonMap("next", value);
        }
        return value;
    }
}
