package benchmark;

import graphql.ExecutionInput;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2, timeUnit = TimeUnit.NANOSECONDS)
public class ChainedInstrumentationBenchmark {

    @Param({"0", "1", "10"})
    public int num;

    ChainedInstrumentation chainedInstrumentation;
    GraphQLSchema schema;
    InstrumentationExecutionParameters parameters;
    InstrumentationState instrumentationState;

    @Setup(Level.Trial)
    public void setUp() throws ExecutionException, InterruptedException {
        GraphQLObjectType queryType = newObject()
                .name("benchmarkQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("benchmark"))
                .build();
        schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("benchmark").build();
        InstrumentationCreateStateParameters createStateParameters = new InstrumentationCreateStateParameters(schema, executionInput);

        List<Instrumentation> instrumentations = Collections.nCopies(num, new SimplePerformantInstrumentation());
        chainedInstrumentation = new ChainedInstrumentation(instrumentations);
        instrumentationState = chainedInstrumentation.createStateAsync(createStateParameters).get();
        parameters = new InstrumentationExecutionParameters(executionInput, schema, instrumentationState);
    }

    @Benchmark
    public GraphQLSchema benchmarkInstrumentSchema() {
        return chainedInstrumentation.instrumentSchema(schema, parameters, instrumentationState);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include("benchmark.ChainedInstrumentationBenchmark")
                .forks(1)
                .build();

        new Runner(opt).run();
    }

}
