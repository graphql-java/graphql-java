package benchmark;

import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ValidatorBenchmark {

    private static class Scenario {
        public final GraphQLSchema schema;
        public final Document document;

        Scenario(GraphQLSchema schema, Document document) {
            this.schema = schema;
            this.document = document;
        }
    }

    @State(Scope.Benchmark)
    public static class MyState {
        Scenario largeSchema1;
        Scenario largeSchema4;
        Scenario manyFragments;

        @Setup
        public void setup() {
            largeSchema1 = load("large-schema-1.graphqls", "large-schema-1-query.graphql");
            largeSchema4 = load("large-schema-4.graphqls", "large-schema-4-query.graphql");
            manyFragments = load("many-fragments.graphqls", "many-fragments-query.graphql");
        }

        private Scenario load(String schemaPath, String queryPath) {
            try {
                String schemaString = BenchmarkUtils.loadResource(schemaPath);
                String query = BenchmarkUtils.loadResource(queryPath);
                GraphQLSchema schema = SchemaGenerator.createdMockedSchema(schemaString);
                Document document = Parser.parse(query);
                return new Scenario(schema, document);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    private List<ValidationError> run(Scenario scenario) {
        Validator validator = new Validator();
        return validator.validateDocument(scenario.schema, scenario.document, Locale.ENGLISH);
    }

    @Benchmark
    public List<ValidationError> largeSchema1(MyState state) {
        return run(state.largeSchema1);
    }

    @Benchmark
    public List<ValidationError> largeSchema4(MyState state) {
        return run(state.largeSchema4);
    }

    @Benchmark
    public List<ValidationError> manyFragments(MyState state) {
        return run(state.manyFragments);
    }
}
