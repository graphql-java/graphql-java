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
@Warmup(iterations = 20, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(2)
public class GoodFaithValidationBenchmark {
    @Param({"tiny", "minimal", "simple", "fragments", "wideScalars", "wide", "wideLarge", "fragmentHeavy", "deep", "multiOperation", "mixedAbstract", "introspection"})
    public String scenario;

    private GraphQLSchema schema;
    private Document document;

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema(schemaDefinition());
        document = Parser.parse(query());
        assertTrue(validate().isEmpty());
    }

    private String schemaDefinition() {
        if ("mixedAbstract".equals(scenario)) {
            return "type Query { node: Node } interface Node { name: String child: Node } "
                    + "type A implements Node { name: String child: A extraA: String } "
                    + "type B implements Node { name: String child: B extraB: String }";
        }
        return "type Query { name: String node: Node } type Node { name: String child: Node }";
    }

    private String query() {
        switch (scenario) {
            case "tiny":
                return "{ name }";
            case "minimal":
                return "{ node { name } }";
            case "simple":
                return "{ node { name child { name } } }";
            case "fragments":
                return repeatedFields(2, "node { ...F }") + fragments();
            case "wideScalars":
                return repeatedFields(1000, "name");
            case "wide":
                return repeatedFields(200, "node { name child { name } }");
            case "wideLarge":
                return repeatedFields(1000, "node { name child { name } }");
            case "fragmentHeavy":
                return repeatedFields(1000, "node { ...F }") + fragments();
            case "deep":
                return "{ node { " + "child { ".repeat(30) + "name" + " }".repeat(30) + " } }";
            case "multiOperation":
                return multipleOperations();
            case "mixedAbstract":
                return repeatedFields(200, "node { name child { name } "
                        + "... on A { name child { name extraA } } ... on B { name child { name extraB } } }");
            case "introspection":
                return IntrospectionQuery.INTROSPECTION_QUERY;
            default:
                throw new IllegalArgumentException(scenario);
        }
    }

    private String repeatedFields(int count, String selection) {
        StringBuilder query = new StringBuilder("{");
        for (int index = 0; index < count; index++) {
            query.append("n").append(index).append(": ").append(selection).append(' ');
        }
        return query.append("}").toString();
    }

    private String multipleOperations() {
        StringBuilder query = new StringBuilder();
        String selections = repeatedFields(25, "node { ...F }");
        for (int index = 0; index < 20; index++) {
            query.append("query Op").append(index).append(' ').append(selections);
        }
        return query.append(fragments()).toString();
    }

    private String fragments() {
        return "fragment F on Node { name child { ...G } } fragment G on Node { name }";
    }

    @Benchmark
    public List<ValidationError> validate() {
        return new Validator().validateDocument(schema, document, Locale.ENGLISH);
    }
}
