package benchmark;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

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

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.validation.Validator;

import static com.google.common.io.Resources.getResource;

import static graphql.Assert.assertTrue;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Threads(1)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 10, time = 10)
@Fork(3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ValidatorBenchmark {

    @State(Scope.Benchmark)
    public static class MyState {
        GraphQLSchema schema;
        Document document;

        @Setup
        public void setup() {
            try {
                String schemaString = readFromClasspath("large-schema-4.graphqls");
                String query = readFromClasspath("large-schema-4-query.graphql");
                schema = SchemaGenerator.createdMockedSchema(schemaString);
                document = Parser.parse(query);

                // make sure this is a valid query overall
                GraphQL graphQL = GraphQL.newGraphQL(schema).build();
                ExecutionResult executionResult = graphQL.execute(query);
                assertTrue(executionResult.getErrors().size() == 0);
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
    public void runValidator(MyState state) {
        Validator validator = new Validator();
        validator.validateDocument(state.schema, state.document);
    }
}
