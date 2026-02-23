package performance;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.i18n.I18n;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.validation.LanguageTraversal;
import graphql.validation.OperationValidationRule;
import graphql.validation.OperationValidator;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorCollector;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static graphql.Assert.assertTrue;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class OverlappingFieldValidationPerformance {


    static String schemaSdl = " type Query { viewer: Viewer } interface Abstract { field: Abstract leaf: Int } interface Abstract1 { field: Abstract leaf: Int } interface Abstract2 { field: Abstract leaf: Int }" +
            " type Concrete1 implements Abstract1{ field: Abstract leaf: Int}  " +
            "type Concrete2 implements Abstract2{ field: Abstract leaf: Int} " +
            "type Viewer { xingId: XingId } type XingId { firstName: String! lastName: String! }";

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        GraphQLSchema schema2;
        Document document;

        @Param({"100"})
        int size;

        Document overlapFrag;
        Document overlapNoFrag;
        Document noOverlapFrag;
        Document noOverlapNoFrag;
        Document repeatedFields;
        Document deepAbstractConcrete;

        @Setup
        public void setup() {
            try {
                overlapFrag = makeQuery(size, true, true);
                overlapNoFrag = makeQuery(size, true, false);
                noOverlapFrag = makeQuery(size, false, true);
                noOverlapNoFrag = makeQuery(size, false, false);
                repeatedFields = makeRepeatedFieldsQuery(size);
                deepAbstractConcrete = makeDeepAbstractConcreteQuery(size);


                schema2 = SchemaGenerator.createdMockedSchema(schemaSdl);

                String schemaString = PerformanceTestingUtils.loadResource("large-schema-4.graphqls");
                String query = PerformanceTestingUtils.loadResource("large-schema-4-query.graphql");
                schema = SchemaGenerator.createdMockedSchema(schemaString);
                document = Parser.parse(query);

                // make sure this is a valid query overall
                GraphQL graphQL = GraphQL.newGraphQL(schema).build();
                ExecutionResult executionResult = graphQL.execute(query);
                assertTrue(executionResult.getErrors().size() == 0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void overlappingFieldValidationAvgTime(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema, myState.document));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void overlappingFieldValidationThroughput(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema, myState.document));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkRepeatedFields(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema2, myState.repeatedFields));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkOverlapFrag(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema2, myState.overlapFrag));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkOverlapNoFrag(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema2, myState.overlapNoFrag));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkNoOverlapFrag(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema2, myState.noOverlapFrag));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkNoOverlapNoFrag(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema2, myState.noOverlapNoFrag));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkDeepAbstractConcrete(MyState myState, Blackhole blackhole) {
        blackhole.consume(validateQuery(myState.schema2, myState.deepAbstractConcrete));
    }

    private List<ValidationError> validateQuery(GraphQLSchema schema, Document document) {
        ValidationErrorCollector errorCollector = new ValidationErrorCollector();
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH);
        ValidationContext validationContext = new ValidationContext(schema, document, i18n);
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                rule -> rule == OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED);
        LanguageTraversal languageTraversal = new LanguageTraversal();
        languageTraversal.traverse(document, operationValidator);
        Assert.assertTrue(errorCollector.getErrors().size() == 0);
        return errorCollector.getErrors();
    }


    private static Document makeQuery(int size, boolean overlapping, boolean fragments) {
        if (fragments) {
            return makeQueryWithFragments(size, overlapping);
        } else {
            return makeQueryWithoutFragments(size, overlapping);
        }
    }

    private static Document makeRepeatedFieldsQuery(int size) {
        StringBuilder b = new StringBuilder();

        b.append(" query testQuery {  viewer {   xingId {");

        b.append("firstName\n".repeat(Math.max(0, size)));

        b.append("} } }");

        return Parser.parse(b.toString());
    }


    private static Document makeQueryWithFragments(int size, boolean overlapping) {
        StringBuilder b = new StringBuilder();

        for (int i = 1; i <= size; i++) {
            if (overlapping) {
                b.append(" fragment mergeIdenticalFields" + i + " on Query {viewer { xingId { firstName lastName  }}}");
            } else {
                b.append("fragment mergeIdenticalFields" + i + " on Query {viewer" + i + " {  xingId" + i + " {  firstName" + i + "  lastName" + i + "  } }}");
            }

            b.append("\n\n");
        }

        b.append("query testQuery {");
        for (int i = 1; i <= size; i++) {
            b.append("...mergeIdenticalFields" + i + "\n");
        }
        b.append("}");
        return Parser.parse(b.toString());
    }

    private static Document makeQueryWithoutFragments(int size, boolean overlapping) {
        StringBuilder b = new StringBuilder();

        b.append("query testQuery {");

        for (int i = 1; i <= size; i++) {
            if (overlapping) {
                b.append(" viewer {   xingId {      firstName   } } ");
            } else {
                b.append(" viewer" + i + " {    xingId" + i + " {      firstName" + i + "    }  } ");
            }

            b.append("\n\n");
        }

        b.append("}");

        return Parser.parse(b.toString());
    }

    private static Document makeDeepAbstractConcreteQuery(int depth) {
        StringBuilder q = new StringBuilder();

        q.append("fragment multiply on Whatever {   field {      " +
                "... on Abstract1 { field { leaf } }      " +
                "... on Abstract2 { field { leaf } }      " +
                "... on Concrete1 { field { leaf } }      " +
                "... on Concrete2 { field { leaf } }    } } " +
                "query DeepAbstractConcrete { ");

        for (int i = 1; i <= depth; i++) {
            q.append("field { ...multiply ");
        }

        for (int i = 1; i <= depth; i++) {
            q.append(" }");
        }

        q.append("\n}");

        return Parser.parse(q.toString());
    }
}
