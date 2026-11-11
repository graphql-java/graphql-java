package benchmark;

import graphql.introspection.IntrospectionQuery;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.validation.QueryComplexityLimits;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
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
@Warmup(iterations = 9, time = 5)
@Measurement(iterations = 6, time = 5)
@Fork(2)
public class GeneralQueryComplexityBenchmark {
    @Param({"tiny", "minimal", "simple", "wideScalars", "wideTree", "fragments", "fragmentHeavy",
            "duplicates", "deep", "mixedAbstract", "multiOperation", "directives", "introspection",
            "aliasFanout", "duplicateFanout", "invalidFields", "mixedUnion", "mutation", "subscription", "sharedLeafGroup"})
    public String scenario;

    @Param({"default"})
    public String limits;

    private GraphQLSchema schema;
    private Document document;
    private QueryComplexityLimits complexityLimits;

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema(schemaDefinition());
        document = Parser.parse(query());
        complexityLimits = configuredLimits();
        List<ValidationError> errors = validate();
        if ("duplicateFanout".equals(scenario) || "sharedLeafGroup".equals(scenario)) {
            int occurrences = "duplicateFanout".equals(scenario) ? 262_145 : 100_200;
            boolean rejected = complexityLimits.getMaxFieldOccurrences() < occurrences;
            assertTrue(errors.isEmpty() != rejected, () -> scenario + ": " + errors);
            assertTrue(errors.stream().allMatch(error -> error.getValidationErrorType() == ValidationErrorType.MaxQueryFieldOccurrencesExceeded));
            return;
        }
        boolean rejected = "invalidFields".equals(scenario) || ("aliasFanout".equals(scenario) && !"disabled".equals(limits));
        assertTrue(errors.isEmpty() != rejected, () -> scenario + ": " + errors);
    }

    private QueryComplexityLimits configuredLimits() {
        if ("disabled".equals(limits)) {
            return QueryComplexityLimits.NONE;
        }
        if ("withoutOccurrences".equals(limits)) {
            return QueryComplexityLimits.newLimits().maxDepth(100).maxMergedFieldCount(10_000)
                    .maxFieldOccurrences(Integer.MAX_VALUE).build();
        }
        if ("highOccurrences".equals(limits)) {
            return QueryComplexityLimits.newLimits().maxDepth(100).maxMergedFieldCount(10_000)
                    .maxFieldOccurrences(Integer.MAX_VALUE - 1).build();
        }
        return QueryComplexityLimits.DEFAULT;
    }

    private String schemaDefinition() {
        if ("mixedAbstract".equals(scenario) || "mixedUnion".equals(scenario)) {
            String rootType = "mixedUnion".equals(scenario) ? "Result" : "Node";
            return "type Query { node: " + rootType + " } union Result = A | B interface Node { name: String child: Node } "
                    + "type A implements Node { name: String child: A extraA: String } "
                    + "type B implements Node { name: String child: B extraB: String }";
        }
        return "type Query { name: String node: Node } type Mutation { node: Node } "
                + "type Subscription { node: Node } type Node { name: String child: Node }";
    }

    private String query() {
        switch (scenario) {
            case "tiny":
                return "{ name }";
            case "minimal":
                return "{ node { name } }";
            case "simple":
                return "{ node { name child { name } } }";
            case "wideScalars":
                return repeatedFields(1000, "name");
            case "wideTree":
                return repeatedFields(1000, "node { name child { name } }");
            case "fragments":
                return repeatedFields(2, "node { ...F }") + fragments();
            case "fragmentHeavy":
                return repeatedFields(1000, "node { ...F }") + fragments();
            case "duplicates":
                return "{ " + "node { name child { name } } ".repeat(1000) + "}";
            case "deep":
                return "{ node { " + "child { ".repeat(88) + "name" + " }".repeat(88) + " } }";
            case "mixedAbstract":
                return repeatedFields(200, "node { name child { name } "
                        + "... on A { name child { name extraA } } ... on B { name child { name extraB } } }");
            case "mixedUnion":
                return repeatedFields(200, "node { ... on A { name child { name extraA } } "
                        + "... on B { name child { name extraB } } }");
            case "mutation":
                return "mutation " + repeatedFields(500, "node { name child { name } }");
            case "subscription":
                return "subscription { node " + repeatedFields(1000, "child { name }") + " }";
            case "sharedLeafGroup":
                return repeatedFields(200, "node { ...Leaves }") + "fragment Leaves on Node { " + "name ".repeat(500) + "}";
            case "multiOperation":
                return multipleOperations();
            case "directives":
                return "query($include: Boolean!) " + repeatedFields(500, "node @include(if: $include) { name child { name } }");
            case "introspection":
                return IntrospectionQuery.INTROSPECTION_QUERY;
            case "aliasFanout":
                return fragmentFanout(true);
            case "duplicateFanout":
                return fragmentFanout(false);
            case "invalidFields":
                return repeatedFields(100, "unknown");
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

    private String fragmentFanout(boolean aliases) {
        StringBuilder query = new StringBuilder("{ node { ...F0 } }");
        for (int index = 0; index < 18; index++) {
            String spread = "...F" + (index + 1);
            String selections = aliases ? "a: child { " + spread + " } b: child { " + spread + " }" : spread + " " + spread;
            query.append("fragment F").append(index).append(" on Node { ").append(selections).append(" }");
        }
        return query.append("fragment F18 on Node { name }").toString();
    }

    @Benchmark
    public List<ValidationError> validate() {
        return new Validator().validateDocument(schema, document, rule -> true, Locale.ENGLISH, complexityLimits);
    }
}
