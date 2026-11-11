package benchmark;

import graphql.Internal;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.parser.Parser;
import graphql.parser.antlr.GraphqlLexer;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.validation.QueryComplexityLimits;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import graphql.validation.Validator;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static graphql.Assert.assertTrue;

@Internal
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 9, time = 5)
@Measurement(iterations = 6, time = 5)
@Fork(2)
@Threads(1)
public class FragmentExpansionValidationBenchmark {

    @Param({"direct", "doubling10", "doubling25"})
    public String scenario;

    @Param({"enabled", "disabled", "default"})
    public String occurrences;

    private GraphQLSchema schema;
    private Document document;
    private QueryComplexityLimits complexityLimits;

    @Setup
    public void setup() {
        schema = SchemaGenerator.createdMockedSchema("type Query { node: Node } type Node { name: String }");
        String query = query();
        document = Parser.parse(query);
        int occurrenceLimit = "disabled".equals(occurrences) ? Integer.MAX_VALUE : Integer.MAX_VALUE - 1;
        if ("default".equals(occurrences)) {
            occurrenceLimit = QueryComplexityLimits.DEFAULT_MAX_FIELD_OCCURRENCES;
        }
        complexityLimits = QueryComplexityLimits.newLimits().maxDepth(100).maxMergedFieldCount(10_000)
                .maxFieldOccurrences(occurrenceLimit).build();
        verifyFixture(query);
        verifyMergedFieldCount();
        List<ValidationError> errors = validate();
        boolean rejected = "default".equals(occurrences) && "doubling25".equals(scenario);
        assertTrue(errors.isEmpty() != rejected);
        assertTrue(errors.stream().allMatch(error -> error.getValidationErrorType() == ValidationErrorType.MaxQueryFieldOccurrencesExceeded));
    }

    private String query() {
        if ("direct".equals(scenario)) {
            return "{node{name}}";
        }
        int levels = "doubling10".equals(scenario) ? 10 : 25;
        StringBuilder query = new StringBuilder("{node{...F0}}");
        for (int index = 0; index < levels; index++) {
            query.append("fragment F").append(index).append(" on Node{...F").append(index + 1)
                    .append(" ...F").append(index + 1).append('}');
        }
        return query.append("fragment F").append(levels).append(" on Node{name}").toString();
    }

    private void verifyFixture(String query) {
        assertTrue(countNodes(true) == 2);
        if (!"doubling25".equals(scenario)) {
            return;
        }
        assertTrue(query.length() == 886);
        assertTrue(countGrammarTokens(query) == 265);
        assertTrue(countNodes(false) == 135);
    }

    private int countNodes(boolean fieldsOnly) {
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(document);
        int count = 0;
        while (!pending.isEmpty()) {
            Node<?> node = pending.pop();
            count += !fieldsOnly || node instanceof Field ? 1 : 0;
            pending.addAll(node.getChildren());
        }
        return count;
    }

    private int countGrammarTokens(String query) {
        GraphqlLexer lexer = new GraphqlLexer(CharStreams.fromString(query));
        int count = 0;
        Token token;
        do {
            token = lexer.nextToken();
            count += token.getChannel() == Token.DEFAULT_CHANNEL ? 1 : 0;
        } while (token.getType() != Token.EOF);
        return count;
    }

    private void verifyMergedFieldCount() {
        QueryComplexityLimits twoFields = QueryComplexityLimits.newLimits().maxMergedFieldCount(2).maxDepth(2)
                .maxFieldOccurrences(Integer.MAX_VALUE).build();
        assertTrue(new Validator().validateDocument(schema, document, rule -> true, Locale.ENGLISH, twoFields).isEmpty());
        QueryComplexityLimits oneField = QueryComplexityLimits.newLimits().maxMergedFieldCount(1).maxDepth(2)
                .maxFieldOccurrences(Integer.MAX_VALUE).build();
        List<ValidationError> errors = new Validator().validateDocument(schema, document, rule -> true, Locale.ENGLISH, oneField);
        assertTrue(errors.size() == 1 && errors.get(0).getValidationErrorType() == ValidationErrorType.MaxQueryMergedFieldCountExceeded);
    }

    @Benchmark
    public List<ValidationError> validate() {
        return new Validator().validateDocument(schema, document, rule -> true, Locale.ENGLISH, complexityLimits);
    }
}
