package benchmark;

import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static graphql.normalized.ExecutableNormalizedOperationFactory.*;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(2)
public class ENFBenchmarkDeepIntrospection {

    @Param({"2", "10", "20"})
    int howDeep = 2;

    String query = "";

    GraphQLSchema schema;
    Document document;

    @Setup(Level.Trial)
    public void setUp() {
        String schemaString = BenchmarkUtils.loadResource("large-schema-2.graphqls");
        schema = SchemaGenerator.createdMockedSchema(schemaString);

        query = createDeepQuery(howDeep);
        document = Parser.parse(query);
    }
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutableNormalizedOperation benchMarkAvgTime() {
        ExecutableNormalizedOperationFactory.Options options  = ExecutableNormalizedOperationFactory.Options.defaultOptions();
        ExecutableNormalizedOperation executableNormalizedOperation = createExecutableNormalizedOperation(schema,
                document,
                null,
                CoercedVariables.emptyVariables(),
                options);
        return executableNormalizedOperation;
    }

    public static void main(String[] args) throws RunnerException {
        runAtStartup();

        Options opt = new OptionsBuilder()
                .include("benchmark.ENFBenchmarkDeepIntrospection")
                .build();

        new Runner(opt).run();
    }

    private static void runAtStartup() {

        ENFBenchmarkDeepIntrospection benchmarkIntrospection = new ENFBenchmarkDeepIntrospection();
        benchmarkIntrospection.howDeep = 2;

        BenchmarkUtils.runInToolingForSomeTimeThenExit(
                benchmarkIntrospection::setUp,
                () -> { while (true) { benchmarkIntrospection.benchMarkAvgTime(); }},
                () ->{}
        );
    }



    private static String createDeepQuery(int depth) {
        String result = "query test {\n" +
                "  __schema {\n" +
                "    types {\n" +
                "      ...F1\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        for (int i = 1; i < depth; i++) {
            result += "        fragment F" + i + " on __Type {\n" +
                    "          fields {\n" +
                    "            type {\n" +
                    "              ...F" + (i + 1) +"\n" +
                    "            }\n" +
                    "          }\n" +
                    "\n" +
                    "          ofType {\n" +
                    "            ...F"+ (i + 1) + "\n" +
                    "          }\n" +
                    "        }\n";
        }
        result += "        fragment F" + depth + " on __Type {\n" +
                "          fields {\n" +
                "            type {\n" +
                "name\n" +
                "            }\n" +
                "          }\n" +
                "}\n";
        return result;
    }

}
