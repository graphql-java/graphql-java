package performance;

import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.normalized.ExecutableNormalizedOperationToAstCompiler;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class ENToAst1Performance {

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        Document document;
        ExecutableNormalizedOperation operation;

        @Setup
        public void setup() {
            try {
                String schemaString = PerformanceTestingUtils.loadResource("large-schema-1.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);

                String query = PerformanceTestingUtils.loadResource("large-schema-1-query.graphql");
                document = Parser.parse(query);

                operation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(schema, document, null, CoercedVariables.emptyVariables());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAvgTime(MyState myState, Blackhole blackhole) {
        runImpl(myState, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkThroughput(MyState myState, Blackhole blackhole) {
        runImpl(myState, blackhole);
    }

    private void runImpl(MyState myState, Blackhole blackhole) {
        ExecutableNormalizedOperationToAstCompiler.CompilerResult result = ExecutableNormalizedOperationToAstCompiler.compileToDocument(
                myState.schema,
                OperationDefinition.Operation.QUERY,
                myState.operation.getOperationName(),
                myState.operation.getTopLevelFields(),
                myState.operation.getNormalizedFieldToQueryDirectives(),
                (executableNormalizedField, argName, normalizedInputValue) -> false
        );
        blackhole.consume(result);
    }


}
