package benchmark;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.io.Resources;
import graphql.language.Document;
import graphql.language.Field;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitorStub;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2, timeUnit = TimeUnit.NANOSECONDS)
public class NQBenchmark2 {

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        Document document;

        @Setup
        public void setup() {
            try {
                String schemaString = readFromClasspath("large-schema-2.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);

                String query = readFromClasspath("large-schema-2-query.graphql");
                document = Parser.parse(query);
            } catch (Exception e) {
                System.out.println(e);
                throw new RuntimeException(e);
            }
        }

        private String readFromClasspath(String file) throws IOException {
            URL url = getResource(file);
            return Resources.toString(url, Charsets.UTF_8);
        }
    }

    @Benchmark
    @Warmup(iterations = 2)
    @Measurement(iterations = 3, time = 10)
    @Threads(1)
    @Fork(3)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutableNormalizedOperation benchMarkAvgTime(MyState myState) throws ExecutionException, InterruptedException {
        ExecutableNormalizedOperation executableNormalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(myState.schema, myState.document, null, Collections.emptyMap());
//        System.out.println("fields size:" + normalizedQuery.getFieldToNormalizedField().size());
        return executableNormalizedOperation;
    }

    public static void main(String[] args) {
        MyState myState = new MyState();
        myState.setup();
        ExecutableNormalizedOperation executableNormalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(myState.schema, myState.document, null, Collections.emptyMap());
//        System.out.println(printTree(normalizedQuery));
        ImmutableListMultimap<Field, ExecutableNormalizedField> fieldToNormalizedField = executableNormalizedOperation.getFieldToNormalizedField();
        System.out.println(fieldToNormalizedField.size());
//        for (Field field : fieldToNormalizedField.keySet()) {
//            System.out.println("field" + field);
//            System.out.println("nf count:" + fieldToNormalizedField.get(field).size());
//            if (field.getName().equals("field49")) {
//                ImmutableList<NormalizedField> normalizedFields = fieldToNormalizedField.get(field);
//                for (NormalizedField nf : normalizedFields) {
//                    System.out.println(nf);
//                }
//            }
//        }
//        System.out.println("fields size:" + normalizedQuery.getFieldToNormalizedField().size());
    }

    static List<String> printTree(ExecutableNormalizedOperation queryExecutionTree) {
        List<String> result = new ArrayList<>();
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst(ExecutableNormalizedField::getChildren);
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            public TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode();
                result.add(queryExecutionField.printDetails());
                return TraversalControl.CONTINUE;
            }
        });
        return result;
    }
}
