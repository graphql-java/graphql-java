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

import static graphql.Assert.assertTrue;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SubscriptionValidationBenchmark {

    private GraphQLSchema schema;
    private Document complexSubscriptionDocument;

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema(schemaSdl());
        complexSubscriptionDocument = Parser.parse(complexSubscriptionQuery());
        assertTrue(validate(complexSubscriptionDocument).isEmpty());
    }

    @Benchmark
    public List<ValidationError> complexSubscription() {
        return validate(complexSubscriptionDocument);
    }

    private List<ValidationError> validate(Document document) {
        Validator validator = new Validator();
        return validator.validateDocument(schema, document, Locale.ENGLISH);
    }

    private static String schemaSdl() {
        return String.join("\n",
                "schema {",
                "  query: Query",
                "  subscription: Subscription",
                "}",
                "",
                "type Query {",
                "  event: Event",
                "}",
                "",
                "type Subscription {",
                "  event: Event",
                "}",
                "",
                "interface Node {",
                "  id: ID",
                "}",
                "",
                "interface Named {",
                "  name: String",
                "}",
                "",
                "type Event implements Node & Named {",
                "  id: ID",
                "  name: String",
                "  timestamp: String",
                "  payload: Payload",
                "  related: Event",
                "}",
                "",
                "type Payload {",
                "  id: ID",
                "  value: String",
                "  nested: Payload",
                "  metrics: Metrics",
                "}",
                "",
                "type Metrics {",
                "  count: Int",
                "  label: String",
                "}");
    }

    private static String complexSubscriptionQuery() {
        StringBuilder query = new StringBuilder();
        query.append("subscription ComplexSubscription {\n");
        appendRootFragmentSpreads(query);
        query.append("}\n");
        appendRootFragments(query);
        appendEventFragments(query);
        appendSharedEventFragment(query);
        return query.toString();
    }

    private static void appendRootFragmentSpreads(StringBuilder query) {
        for (int i = 0; i < 50; i++) {
            query.append("  ...RootFragment").append(i).append("\n");
        }
    }

    private static void appendRootFragments(StringBuilder query) {
        for (int i = 0; i < 50; i++) {
            query.append("fragment RootFragment").append(i).append(" on Subscription {\n");
            query.append("  event {\n");
            query.append("    ...EventFragment").append(i % 20).append("\n");
            query.append("    ...SharedEventFields\n");
            query.append("    related {\n");
            query.append("      ...SharedEventFields\n");
            query.append("    }\n");
            query.append("  }\n");
            query.append("}\n");
        }
    }

    private static void appendEventFragments(StringBuilder query) {
        for (int i = 0; i < 20; i++) {
            query.append("fragment EventFragment").append(i).append(" on Event {\n");
            query.append("  id\n");
            query.append("  name\n");
            query.append("  payload {\n");
            query.append("    value\n");
            query.append("    metrics {\n");
            query.append("      count\n");
            query.append("      label\n");
            query.append("    }\n");
            query.append("  }\n");
            query.append("}\n");
        }
    }

    private static void appendSharedEventFragment(StringBuilder query) {
        query.append("fragment SharedEventFields on Event {\n");
        query.append("  id\n");
        query.append("  name\n");
        query.append("  timestamp\n");
        query.append("  payload {\n");
        query.append("    nested {\n");
        query.append("      value\n");
        query.append("      metrics {\n");
        query.append("        count\n");
        query.append("      }\n");
        query.append("    }\n");
        query.append("  }\n");
        query.append("}\n");
    }
}
