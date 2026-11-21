package performance;

import graphql.ExecutionInput;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionContextBuilder;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.normalized.NormalizedDocument;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class DFSelectionSetPerformance {
    private ExecutionContext context;
    private MergedField field;
    private GraphQLOutputType fieldType;

    @Setup
    public void setup() throws Exception {
        GraphQLSchema schema = SchemaGenerator.createdMockedSchema(PerformanceTestingUtils.loadResource("large-schema-2.graphqls"));
        ExecutionInput input = ExecutionInput.newExecutionInput(PerformanceTestingUtils.loadResource("large-schema-2-query.graphql"))
                .graphQLContext(Map.of(NormalizedDocument.ENABLED, true)).build();
        ParseAndValidateResult result = ParseAndValidate.parseAndValidate(schema, input);
        assertTrue(!result.isFailure(), () -> result.getErrors().toString());
        NormalizedDocument document = assertNotNull(result.getNormalizedDocument());
        Field root = (Field) document.getOperations().get(0).getSource().getSelectionSet().getSelections().get(0);
        field = MergedField.newMergedField(root).build();
        fieldType = assertNotNull(schema.getQueryType().getFieldDefinition(root.getName())).getType();
        context = ExecutionContextBuilder.newExecutionContextBuilder().executionId(graphql.execution.ExecutionId.generate()).graphQLSchema(schema)
                .graphQLContext(input.getGraphQLContext()).locale(input.getLocale())
                .document(result.getDocument()).normalizedDocument(document).build();
    }

    @Benchmark
    public List<SelectedField> benchMarkAvgTime() {
        return collector().getFields("wontBeFound");
    }

    @Benchmark
    public List<SelectedField> benchMarkAvgTime_getImmediateFields() {
        return collector().getImmediateFields();
    }

    private DataFetchingFieldSelectionSet collector() {
        return DataFetchingFieldSelectionSetImpl.newCollector(context, field, fieldType, 1);
    }
}
