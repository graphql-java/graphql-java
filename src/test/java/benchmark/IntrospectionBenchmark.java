package benchmark;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.DataFetcherResult;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.io.Resources.getResource;

@State(Scope.Benchmark)
public class IntrospectionBenchmark {

    private final GraphQL graphQL;
    private final DFCountingInstrumentation countingInstrumentation = new DFCountingInstrumentation();

    static class DFCountingInstrumentation extends SimpleInstrumentation {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Long> times = new LinkedHashMap<>();

        @Override
        public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
            return (DataFetcher<Object>) env -> {
                long then = System.nanoTime();
                Object value = dataFetcher.get(env);
                long nanos = System.nanoTime() - then;
                DataFetcherResult.Builder<Object> result = DataFetcherResult.newResult().data(value);

                String path = env.getExecutionStepInfo().getPath().toString();
                String prevTypePath = env.getLocalContext();

                Object source = env.getSource();
                if (isSchemaTypesFetch(env, source)) {
                    String typeName = ((GraphQLNamedType) source).getName();

                    String prefix = "/__schema/types[" + typeName + "]";
                    result.localContext(prefix);
                    prevTypePath = prefix;
                }
                if (prevTypePath != null) {
                    path = path.replaceAll("/__schema/types\\[.*\\]", prevTypePath);
                }
                counts.compute(path, (k, v) -> v == null ? 1 : v++);
                if (nanos > 200_000) {
                    times.compute(path, (k, v) -> v == null ? nanos : v + nanos);
                }
                return result.build();
            };
        }

        private boolean isSchemaTypesFetch(DataFetchingEnvironment env, Object source) {
            String parentPath = env.getExecutionStepInfo().getParent().getPath().getPathWithoutListEnd().toString();
            return "/__schema/types".equals(parentPath) && source instanceof GraphQLNamedType;
        }
    }

    public IntrospectionBenchmark() {
        String largeSchema = readFromClasspath("large-schema-4.graphqls");
        GraphQLSchema graphQLSchema = SchemaGenerator.createdMockedSchema(largeSchema);
        graphQL = GraphQL.newGraphQL(graphQLSchema).instrumentation(countingInstrumentation).build();
    }


    private static String readFromClasspath(String file) {
        URL url = getResource(file);
        try {
            return Resources.toString(url, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        IntrospectionBenchmark introspectionBenchmark = new IntrospectionBenchmark();
//        while (true) {
//            long then = System.currentTimeMillis();
//            ExecutionResult er = introspectionBenchmark.benchMarkIntrospection();
//            long ms = System.currentTimeMillis() - then;
//            System.out.println("Took " + ms + "ms");
//        }

        introspectionBenchmark.benchMarkIntrospection();

        Map<String, Long> counts = sortByValue(introspectionBenchmark.countingInstrumentation.counts);
        Map<String, Long> times = sortByValue(introspectionBenchmark.countingInstrumentation.times);

        System.out.println("Counts");
        counts.forEach((k, v) -> System.out.printf("C %-70s : %020d\n", k, v));
        System.out.println("Times");
        times.forEach((k, v) -> System.out.printf("T %-70s : %020d\n", k, v));


    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    public ExecutionResult benchMarkIntrospection() {
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

}
