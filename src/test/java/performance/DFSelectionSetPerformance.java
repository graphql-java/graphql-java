package performance;

import benchmark.BenchmarkUtils;
import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.parser.Parser;
import graphql.schema.*;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class DFSelectionSetPerformance {

    @State(Scope.Benchmark)
    public static class MyState {

        public ExecutableNormalizedField normalisedField;
        public GraphQLOutputType outputFieldType;
        GraphQLSchema schema;
        Document document;

        @Setup
        public void setup() {
            try {
                String schemaString = PerformanceTestingUtils.loadResource("large-schema-2.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);

                String query = PerformanceTestingUtils.loadResource("large-schema-2-query.graphql");
                document = Parser.parse(query);

                ExecutableNormalizedOperation executableNormalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(schema, document, null, CoercedVariables.emptyVariables());

                normalisedField = executableNormalizedOperation.getTopLevelFields().get(0);

                outputFieldType = schema.getObjectType("Object42");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAvgTime(MyState myState, Blackhole blackhole) {
        List<SelectedField> fields = getSelectedFields(myState);
        blackhole.consume(fields);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkThroughput(MyState myState, Blackhole blackhole) {
        List<SelectedField> fields = getSelectedFields(myState);
        blackhole.consume(fields);
    }

    private List<SelectedField> getSelectedFields(MyState myState) {
        DataFetchingFieldSelectionSet dataFetchingFieldSelectionSet = DataFetchingFieldSelectionSetImpl.newCollector(myState.schema, myState.outputFieldType, () -> myState.normalisedField);
        return dataFetchingFieldSelectionSet.getFields("wontBeFound");
    }

    public static void mainX(String[] args) throws InterruptedException {
        MyState myState = new MyState();
        myState.setup();

        while (true) {
            List<SelectedField> selectedFields = new DFSelectionSetPerformance().getSelectedFields(myState);
            Thread.sleep(500);
        }
    }

}
