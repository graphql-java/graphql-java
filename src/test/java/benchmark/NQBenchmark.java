package benchmark;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.language.Document;
import graphql.normalized.NormalizedQuery;
import graphql.normalized.NormalizedQueryFactory;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.io.Resources.getResource;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2, timeUnit = TimeUnit.NANOSECONDS)
public class NQBenchmark {

    @org.openjdk.jmh.annotations.State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        Document document;

        @Setup
        public void setup() {
            try {
                String schemaString = readFromClasspath("large-schema-1.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);

                String query = readFromClasspath("large-schema-1-query.graphql");
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
    public NormalizedQuery benchMarkAvgTime(MyState myState) throws ExecutionException, InterruptedException {
        NormalizedQuery normalizedQuery = NormalizedQueryFactory.createNormalizedQuery(myState.schema, myState.document, null, Collections.emptyMap());
//        System.out.println("fields size:" + normalizedQuery.getFieldToNormalizedField().size());
        return normalizedQuery;
    }


}
