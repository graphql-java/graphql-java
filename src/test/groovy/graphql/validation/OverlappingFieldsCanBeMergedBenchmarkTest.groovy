package graphql.validation

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaGenerator
import spock.lang.Shared
import spock.lang.Specification

/**
 * These tests mirror the JMH benchmarks in OverlappingFieldValidationPerformance
 * and OverlappingFieldValidationBenchmark. They exercise the same validation paths
 * and assert zero errors, ensuring the overlapping fields rule does not produce
 * false positives (e.g. from null type handling).
 */
class OverlappingFieldsCanBeMergedBenchmarkTest extends Specification {

    static String schemaSdl = " type Query { viewer: Viewer } interface Abstract { field: Abstract leaf: Int } interface Abstract1 { field: Abstract leaf: Int } interface Abstract2 { field: Abstract leaf: Int }" +
            " type Concrete1 implements Abstract1{ field: Abstract leaf: Int}  " +
            "type Concrete2 implements Abstract2{ field: Abstract leaf: Int} " +
            "type Viewer { xingId: XingId } type XingId { firstName: String! lastName: String! }"

    @Shared
    GraphQLSchema schema

    @Shared
    GraphQLSchema schema2

    @Shared
    Document largeSchemaDocument

    def setupSpec() {
        String schemaString = loadResource("large-schema-4.graphqls")
        String query = loadResource("large-schema-4-query.graphql")
        schema = SchemaGenerator.createdMockedSchema(schemaString)
        largeSchemaDocument = Parser.parse(query)
        schema2 = SchemaGenerator.createdMockedSchema(schemaSdl)
    }

    private static String loadResource(String name) {
        URL resource = OverlappingFieldsCanBeMergedBenchmarkTest.class.getClassLoader().getResource(name)
        if (resource == null) {
            throw new IllegalArgumentException("missing resource: " + name)
        }
        try (InputStream inputStream = resource.openStream()) {
            return new String(inputStream.readAllBytes(), "UTF-8")
        }
    }

    private List<ValidationError> validateQuery(GraphQLSchema schema, Document document) {
        ValidationErrorCollector errorCollector = new ValidationErrorCollector()
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(schema, document, i18n, QueryComplexityLimits.NONE)
        OperationValidator operationValidator = new OperationValidator(validationContext, errorCollector,
                { r -> r == OperationValidationRule.OVERLAPPING_FIELDS_CAN_BE_MERGED })
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, operationValidator)
        return errorCollector.getErrors()
    }

    // -- Large schema tests (mirrors OverlappingFieldValidationBenchmark) --

    def "large schema query produces no validation errors"() {
        when:
        def errors = validateQuery(schema, largeSchemaDocument)

        then:
        errors.size() == 0
    }

    def "large schema query executes without errors"() {
        when:
        GraphQL graphQL = GraphQL.newGraphQL(schema).build()
        def executionInput = graphql.ExecutionInput.newExecutionInput()
                .query(loadResource("large-schema-4-query.graphql"))
                .graphQLContext([(QueryComplexityLimits.KEY): QueryComplexityLimits.NONE])
                .build()
        ExecutionResult executionResult = graphQL.execute(executionInput)

        then:
        executionResult.errors.size() == 0
    }

    // -- Parameterized tests (mirrors OverlappingFieldValidationPerformance) --

    def "overlapping fields with fragments produce no errors"() {
        given:
        Document doc = makeQueryWithFragments(100, true)

        when:
        def errors = validateQuery(schema2, doc)

        then:
        errors.size() == 0
    }

    def "overlapping fields without fragments produce no errors"() {
        given:
        Document doc = makeQueryWithoutFragments(100, true)

        when:
        def errors = validateQuery(schema2, doc)

        then:
        errors.size() == 0
    }

    def "non-overlapping fields with fragments produce no errors"() {
        given:
        Document doc = makeQueryWithFragments(100, false)

        when:
        def errors = validateQuery(schema2, doc)

        then:
        errors.size() == 0
    }

    def "non-overlapping fields without fragments produce no errors"() {
        given:
        Document doc = makeQueryWithoutFragments(100, false)

        when:
        def errors = validateQuery(schema2, doc)

        then:
        errors.size() == 0
    }

    def "repeated fields produce no errors"() {
        given:
        Document doc = makeRepeatedFieldsQuery(100)

        when:
        def errors = validateQuery(schema2, doc)

        then:
        errors.size() == 0
    }

    def "deep abstract concrete fields produce no errors"() {
        given:
        Document doc = makeDeepAbstractConcreteQuery(100)

        when:
        def errors = validateQuery(schema2, doc)

        then:
        errors.size() == 0
    }

    // -- Query builders (copied from OverlappingFieldValidationPerformance) --

    private static Document makeQueryWithFragments(int size, boolean overlapping) {
        StringBuilder b = new StringBuilder()

        for (int i = 1; i <= size; i++) {
            if (overlapping) {
                b.append(" fragment mergeIdenticalFields" + i + " on Query {viewer { xingId { firstName lastName  }}}")
            } else {
                b.append("fragment mergeIdenticalFields" + i + " on Query {viewer" + i + " {  xingId" + i + " {  firstName" + i + "  lastName" + i + "  } }}")
            }
            b.append("\n\n")
        }

        b.append("query testQuery {")
        for (int i = 1; i <= size; i++) {
            b.append("...mergeIdenticalFields" + i + "\n")
        }
        b.append("}")
        return Parser.parse(b.toString())
    }

    private static Document makeQueryWithoutFragments(int size, boolean overlapping) {
        StringBuilder b = new StringBuilder()

        b.append("query testQuery {")
        for (int i = 1; i <= size; i++) {
            if (overlapping) {
                b.append(" viewer {   xingId {      firstName   } } ")
            } else {
                b.append(" viewer" + i + " {    xingId" + i + " {      firstName" + i + "    }  } ")
            }
            b.append("\n\n")
        }
        b.append("}")
        return Parser.parse(b.toString())
    }

    private static Document makeRepeatedFieldsQuery(int size) {
        StringBuilder b = new StringBuilder()
        b.append(" query testQuery {  viewer {   xingId {")
        b.append("firstName\n".repeat(Math.max(0, size)))
        b.append("} } }")
        return Parser.parse(b.toString())
    }

    private static Document makeDeepAbstractConcreteQuery(int depth) {
        StringBuilder q = new StringBuilder()

        q.append("fragment multiply on Whatever {   field {      " +
                "... on Abstract1 { field { leaf } }      " +
                "... on Abstract2 { field { leaf } }      " +
                "... on Concrete1 { field { leaf } }      " +
                "... on Concrete2 { field { leaf } }    } } " +
                "query DeepAbstractConcrete { ")

        for (int i = 1; i <= depth; i++) {
            q.append("field { ...multiply ")
        }

        for (int i = 1; i <= depth; i++) {
            q.append(" }")
        }

        q.append("\n}")
        return Parser.parse(q.toString())
    }
}
