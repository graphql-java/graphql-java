package benchmark;

import graphql.introspection.IntrospectionQuery;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static graphql.Assert.assertTrue;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class GoodFaithValidationBenchmark {
    @Param({"introspection", "simple", "fragments", "wide"})
    public String scenario;

    private GraphQLSchema schema;
    private Document document;

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema("type Query { node: Node } type Node { name: String child: Node }");
        String query = IntrospectionQuery.INTROSPECTION_QUERY;
        if ("simple".equals(scenario)) {
            query = "{ node { name child { name } } }";
        }
        if ("fragments".equals(scenario)) {
            query = "{ a: node { ...F } b: node { ...F } } fragment F on Node { name child { ...G } } fragment G on Node { name }";
        }
        if ("wide".equals(scenario)) {
            StringBuilder wideQuery = new StringBuilder("{");
            for (int index = 0; index < 200; index++) {
                wideQuery.append("n").append(index).append(": node { name child { name } }");
            }
            query = wideQuery.append("}").toString();
        }
        document = Parser.parse(query);
        assertTrue(validate().isEmpty());
    }

    @Benchmark
    public List<ValidationError> validate() {
        return new Validator().validateDocument(schema, document, Locale.ENGLISH);
    }
}
