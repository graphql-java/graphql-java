package graphql.introspection

import graphql.TestUtil
import graphql.execution.FieldCollector
import graphql.execution.FieldCollectorParameters
import graphql.execution.nextgen.Common
import graphql.execution.nextgen.FieldSubSelection
import graphql.language.Document
import graphql.language.NodeUtil
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.function.Supplier

import static graphql.introspection.IntrospectionUtils.isIntrospectionDocument
import static graphql.introspection.IntrospectionUtils.isIntrospectionFieldSubSelection
import static graphql.introspection.IntrospectionUtils.isIntrospectionOperationDefinition
import static graphql.introspection.IntrospectionUtils.isIntrospectionOperationName
import static graphql.introspection.IntrospectionUtils.isIntrospectionQuery

class IntrospectionUtilsTest extends Specification {
    private static final def WARMING_CYCLES = 3;
    private static final def LOOPS = 1
    private static final def NANOS_IN_MILLIS = 1000000
    private static final def MICROS_IN_MILLIS = 1000

    // The max times below are based on 1 loop with 3 warming cycles.  The values below give are set far higher than
    // what the usual 1 loop, 3 cycle test usually performs.  To the right, of the constant are values indicative of
    // what most tests run at.  However, occasionally you will have tests that hiccups and go higher, hence the
    // raised settings.  Increasing the number of loops, greatly reduces the average microseconds for each test but a
    // single loop with 3 warming cycles should at least be able to attain the below numbers consistently.  The unit
    // tests will always validate the correctness of the utility methods but performance testing is off by default.
    // To enable performance test by setting ENABLE_PERFORMANCE_CHECK to 1 (warmup cycles are not checked for time
    // validation when performance checks are enabled).  Unit tests should be run with performance check on whenever
    // IntrospectionUtils class is modified.  Measurements were taken on a MacBook Pro 15-inch, 2017 with 3.1 GHz
    // Intel Core i7, 16 GB 2133 MHz LPDDR3, and 500 GB SSD.
    private static final def ENABLE_PERFORMANCE_CHECK = 0;
    private static final def MAX_AVG_MICROS_BASELINE = 100L * ENABLE_PERFORMANCE_CHECK // Usually < ~20 μs
    private static final def MAX_AVG_MICROS_OPNAME = 200L * ENABLE_PERFORMANCE_CHECK   // Usually from ~20 to 50 μs
    private static final def MAX_AVG_MICROS_QUERY = 2500L * ENABLE_PERFORMANCE_CHECK   // Usually from ~20 to 800 μs
    private static final def MAX_AVG_MICROS_DOCUMENT = 500L * ENABLE_PERFORMANCE_CHECK // Usually from ~30 to 100 μs
    private static final def MAX_AVG_MICROS_OPDEF = 450L * ENABLE_PERFORMANCE_CHECK    // Usually from ~20 to 80 μs
    private static final def MAX_AVG_MICROS_FSS = 400L * ENABLE_PERFORMANCE_CHECK      // Usually from ~20 to 50 μs

    private static final def INTROSPECTION_SCHEMA = GraphQLSchema.newSchema()
            .query(GraphQLObjectType.newObject().name("Query").build())
            .mutation(GraphQLObjectType.newObject().name("Mutation").build())
            .subscription(GraphQLObjectType.newObject().name("Subscription").build())
            .build();
    private static final def FIELD_COLLECTOR = new FieldCollector();

    private static final def INTROSPECTION_DOCUMENT = TestUtil.parseQuery(IntrospectionQuery.INTROSPECTION_QUERY)
    private static final def INTROSPECTION_DOCUMENT_WITH_SCHEMA_FRAGMENT =
            TestUtil.parseQuery(TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT)
    private static final def INTROSPECTION_DOCUMENT_WITH_ALIAS =
            TestUtil.parseQuery(TestQueries.INTROSPECTION_QUERY_WITH_ALIAS)
    private static final def INTROSPECTION_DOCUMENT_WITH_ALIAS_AND_MIXED_FIELDS =
            TestUtil.parseQuery(TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS)
    private static final def NON_INTROSPECTION_DOCUMENT =
            TestUtil.parseQuery(TestQueries.NON_INTROSPECTION_QUERY)
    private static final def HERO_QUERY_DOCUMENT =
            TestUtil.parseQuery(TestQueries.HERO_QUERY)

    private static final def INTROSPECTION_OPDEF =
            getOperationDefinition(INTROSPECTION_DOCUMENT, IntrospectionQuery.INTROSPECTION_QUERY_NAME)
    private static final def INTROSPECTION_OPDEF_WITH_SCHEMA_FRAGMENT =
            getOperationDefinition(INTROSPECTION_DOCUMENT_WITH_SCHEMA_FRAGMENT,
                    TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME)
    private static final def INTROSPECTION_OPDEF_WITH_ALIAS =
            getOperationDefinition(INTROSPECTION_DOCUMENT_WITH_ALIAS,
                    TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME)
    private static final def INTROSPECTION_OPDEF_WITH_ALIAS_AND_MIXED_FIELDS =
            getOperationDefinition(INTROSPECTION_DOCUMENT_WITH_ALIAS_AND_MIXED_FIELDS,
                    TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME)
    private static final def NON_INTROSPECTION_OPDEF =
            getOperationDefinition(NON_INTROSPECTION_DOCUMENT, TestQueries.NON_INTROSPECTION_QUERY_NAME)
    private static final def HERO_QUERY_OPDEF =
            getOperationDefinition(HERO_QUERY_DOCUMENT, TestQueries.HERO_QUERY_NAME)

    private static final def INTROSPECTION_FSS =
            getFieldSubSelection(INTROSPECTION_DOCUMENT, IntrospectionQuery.INTROSPECTION_QUERY_NAME)
    private static final def INTROSPECTION_FSS_WITH_SCHEMA_FRAGMENT =
            getFieldSubSelection(INTROSPECTION_DOCUMENT_WITH_SCHEMA_FRAGMENT,
                    TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME)
    private static final def INTROSPECTION_FSS_WITH_ALIAS =
            getFieldSubSelection(INTROSPECTION_DOCUMENT_WITH_ALIAS,
                    TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME)
    private static final def INTROSPECTION_FSS_WITH_ALIAS_AND_MIXED_FIELDS =
            getFieldSubSelection(INTROSPECTION_DOCUMENT_WITH_ALIAS_AND_MIXED_FIELDS,
                    TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME)
    private static final def NON_INTROSPECTION_FSS =
            getFieldSubSelection(NON_INTROSPECTION_DOCUMENT, TestQueries.NON_INTROSPECTION_QUERY_NAME)
    private static final def HERO_QUERY_FSS =
            getFieldSubSelection(HERO_QUERY_DOCUMENT,
                    TestQueries.HERO_QUERY_NAME)


    // Test for comparing performance of the introspection utility methods against this baseline which just returns
    // true
    def "Baseline true test result TRUE"() {
        expect:
        testAndMeasureTime("Baseline true loop", { -> true }, true, MAX_AVG_MICROS_BASELINE)
    }


    // isIntrospectionOperationName Tests
    def "isIntrospectionOperationName Tests"() {
        expect:
        // False Tests
        testAndMeasureTime("isIntrospectionOperationName 'null'",
                { -> isIntrospectionOperationName(null) },
                false, MAX_AVG_MICROS_OPNAME)
        testAndMeasureTime("isIntrospectionOperationName ''",
                { -> isIntrospectionOperationName("") },
                false, MAX_AVG_MICROS_OPNAME)
        testAndMeasureTime("isIntrospectionOperationName '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationName(TestQueries.NON_INTROSPECTION_QUERY_NAME) },
                false, MAX_AVG_MICROS_OPNAME)

        // True Tests
        testAndMeasureTime("isIntrospectionOperationName '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationName(IntrospectionQuery.INTROSPECTION_QUERY_NAME) },
                true, MAX_AVG_MICROS_OPNAME)
    }


    // isIntrospectionQuery Tests
    def "isIntrospectionQuery Tests"() {
        expect:
        // False Tests
        testAndMeasureTime("isIntrospectionQuery 'null'",
                { -> isIntrospectionQuery((String) null) },
                false, MAX_AVG_MICROS_QUERY)
        testAndMeasureTime("isIntrospectionQuery '",
                { -> isIntrospectionQuery("") },
                false, MAX_AVG_MICROS_QUERY)
        testAndMeasureTime("isIntrospectionQuery '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionQuery(TestQueries.NON_INTROSPECTION_QUERY) },
                false, MAX_AVG_MICROS_QUERY)
        testAndMeasureTime("isIntrospectionQuery '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionQuery(TestQueries.HERO_QUERY) },
                false, MAX_AVG_MICROS_QUERY)

        // True Tests
        testAndMeasureTime("isIntrospectionQuery '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionQuery(IntrospectionQuery.INTROSPECTION_QUERY) },
                true, MAX_AVG_MICROS_QUERY)
        testAndMeasureTime("isIntrospectionQuery '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionQuery(TestQueries.INTROSPECTION_QUERY_WITH_ALIAS) },
                true, MAX_AVG_MICROS_QUERY)
        testAndMeasureTime("isIntrospectionQuery '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionQuery(TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS) },
                true, MAX_AVG_MICROS_QUERY)
        testAndMeasureTime("isIntrospectionQuery '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionQuery(TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT) },
                true, MAX_AVG_MICROS_QUERY)
    }


    // isIntrospectionDocument Tests
    def "isIntrospectionDocument Tests"() {
        expect:
        // ScanType.ANY Tests
        testAndMeasureTime("isIntrospectionDocument ANY '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT,
                        IntrospectionQuery.INTROSPECTION_QUERY_NAME,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ANY '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_ALIAS,
                        TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ANY '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_SCHEMA_FRAGMENT,
                        TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ANY '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_ALIAS_AND_MIXED_FIELDS,
                        TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ANY '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        NON_INTROSPECTION_DOCUMENT,
                        TestQueries.NON_INTROSPECTION_QUERY_NAME,
                        IntrospectionUtils.ScanType.ANY
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ANY '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        HERO_QUERY_DOCUMENT,
                        TestQueries.HERO_QUERY_NAME,
                        IntrospectionUtils.ScanType.ANY
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)

        testAndMeasureTime("isIntrospectionDocument MIXED '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT,
                        IntrospectionQuery.INTROSPECTION_QUERY_NAME,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_ALIAS,
                        TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_SCHEMA_FRAGMENT,
                        TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_ALIAS_AND_MIXED_FIELDS,
                        TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument MIXED '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        NON_INTROSPECTION_DOCUMENT,
                        TestQueries.NON_INTROSPECTION_QUERY_NAME,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument MIXED '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        HERO_QUERY_DOCUMENT,
                        TestQueries.HERO_QUERY_NAME,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)

        // ScanType.ALL Tests
        testAndMeasureTime("isIntrospectionDocument ALL '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT,
                        IntrospectionQuery.INTROSPECTION_QUERY_NAME,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ALL '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_ALIAS,
                        TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ALL '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_SCHEMA_FRAGMENT,
                        TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ALL '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionDocument(
                        INTROSPECTION_DOCUMENT_WITH_ALIAS_AND_MIXED_FIELDS,
                        TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ALL '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        NON_INTROSPECTION_DOCUMENT,
                        TestQueries.NON_INTROSPECTION_QUERY_NAME,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
        testAndMeasureTime("isIntrospectionDocument ALL '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionDocument(
                        HERO_QUERY_DOCUMENT,
                        TestQueries.HERO_QUERY_NAME,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_DOCUMENT)
    }


    // isIntrospectionOperationDefinition Tests
    def "isIntrospectionOperationDefinition Tests"() {
        expect:
        // ScanType.ANY Tests
        testAndMeasureTime("isIntrospectionOperationDefinition ANY '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ANY '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_ALIAS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ANY '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_SCHEMA_FRAGMENT,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ANY '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_ALIAS_AND_MIXED_FIELDS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ANY '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        NON_INTROSPECTION_OPDEF,
                        IntrospectionUtils.ScanType.ANY
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ANY '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        HERO_QUERY_OPDEF,
                        IntrospectionUtils.ScanType.ANY
                ) },
                false, MAX_AVG_MICROS_OPDEF)

        // ScanType.MIXED Tests
        testAndMeasureTime("isIntrospectionOperationDefinition MIXED '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_ALIAS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_SCHEMA_FRAGMENT,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_ALIAS_AND_MIXED_FIELDS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition MIXED '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        NON_INTROSPECTION_OPDEF,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition MIXED '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        HERO_QUERY_OPDEF,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_OPDEF)

        // ScanType.ALL Tests
        testAndMeasureTime("isIntrospectionOperationDefinition ALL '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ALL '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_ALIAS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ALL '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_SCHEMA_FRAGMENT,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ALL '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        INTROSPECTION_OPDEF_WITH_ALIAS_AND_MIXED_FIELDS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ALL '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        NON_INTROSPECTION_OPDEF,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_OPDEF)
        testAndMeasureTime("isIntrospectionOperationDefinition ALL '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionOperationDefinition(
                        HERO_QUERY_OPDEF,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_OPDEF)
    }


    // isIntrospectionFieldSubSelection Tests
    def "isIntrospectionFieldSubSelection Tests"() {
        expect:
        // ScanType.ANY Tests
        testAndMeasureTime("isIntrospectionFieldSubSelection ANY '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ANY '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_ALIAS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ANY '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_SCHEMA_FRAGMENT,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ANY '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_ALIAS_AND_MIXED_FIELDS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ANY '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        NON_INTROSPECTION_FSS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ANY '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        HERO_QUERY_FSS,
                        IntrospectionUtils.ScanType.ANY
                ) },
                false, MAX_AVG_MICROS_FSS)

        // ScanType.MIXED Tests
        testAndMeasureTime("isIntrospectionFieldSubSelection MIXED '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_ALIAS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_SCHEMA_FRAGMENT,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection MIXED '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_ALIAS_AND_MIXED_FIELDS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection MIXED '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        NON_INTROSPECTION_FSS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection MIXED '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        HERO_QUERY_FSS,
                        IntrospectionUtils.ScanType.MIXED
                ) },
                false, MAX_AVG_MICROS_FSS)

        // ScanType.ALL Tests
        testAndMeasureTime("isIntrospectionFieldSubSelection ALL '${IntrospectionQuery.INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ALL '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_ALIAS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ALL '${TestQueries.INTROSPECTION_QUERY_WITH_SCHEMA_FRAGMENT_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_SCHEMA_FRAGMENT,
                        IntrospectionUtils.ScanType.ALL
                ) },
                true, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ALL '${TestQueries.INTROSPECTION_QUERY_WITH_ALIAS_AND_MIXED_FIELDS_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        INTROSPECTION_FSS_WITH_ALIAS_AND_MIXED_FIELDS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ALL '${TestQueries.NON_INTROSPECTION_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        NON_INTROSPECTION_FSS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_FSS)
        testAndMeasureTime("isIntrospectionFieldSubSelection ALL '${TestQueries.HERO_QUERY_NAME}'",
                { -> isIntrospectionFieldSubSelection(
                        HERO_QUERY_FSS,
                        IntrospectionUtils.ScanType.ALL
                ) },
                false, MAX_AVG_MICROS_FSS)
    }


    // Test Helper Methods
    private static def getOperationDefinition(final Document document, final String operationName) {
        return NodeUtil.getOperation(document, operationName).operationDefinition
    }

    private static def getFieldSubSelection(final Document document, final String operationName) {
        def getOperationResult = NodeUtil.getOperation(document, operationName)
        def fragmentsByName = getOperationResult.fragmentsByName
        def operationDefinition = getOperationResult.operationDefinition
        def operationRootType = Common.getOperationRootType(INTROSPECTION_SCHEMA, operationDefinition)

        // For the purpose of checking for the introspection document, we don't actually need the real
        //   schema or the variables map so we just use stubs
        def collectorParameters = FieldCollectorParameters.newParameters()
                .schema(INTROSPECTION_SCHEMA)
                .objectType(operationRootType)
                .fragments(fragmentsByName)
                .variables(Collections.emptyMap())
                .build()
        def mergedSelectionSet = FIELD_COLLECTOR
                .collectFields(collectorParameters, operationDefinition.getSelectionSet())

        return FieldSubSelection.newFieldSubSelection()
                .mergedSelectionSet(mergedSelectionSet)
                .build()
    }

    private static def testAndMeasureTime(final String testName, Supplier<Boolean> testSupplier,
                                          boolean expectedResult,
                                          final long maxAvgMicros) {
        return testAndMeasureTime(testName, testSupplier, expectedResult, WARMING_CYCLES, maxAvgMicros)
    }

    private static def testAndMeasureTime(final String testName,
                                          final Supplier<Boolean> testSupplier,
                                          final boolean expectedResult,
                                          final int warmingCycles,
                                          final long maxAvgMicros) {
        for (int index = 0; index < warmingCycles; index++) {
            testAndMeasureTime("${testName} (Warming Cycle ${index})", testSupplier, expectedResult,
                    true, maxAvgMicros)
        }

        return testAndMeasureTime(testName, testSupplier, expectedResult, false, maxAvgMicros);
    }

    private static def testAndMeasureTime(final String testName, Supplier<Boolean> testSupplier, boolean expectedResult,
                                          final boolean warmingCycle, final long maxAvgMicros) {
        def start = System.nanoTime()
        def result = false

        for (int index = 0; index < LOOPS; index++) {
            result = testSupplier.get()
        }
        def end = System.nanoTime()
        def timeNanos = (end - start)
        def timeMicros = timeNanos / MICROS_IN_MILLIS
        def timeMillis = timeNanos / NANOS_IN_MILLIS
        def avgMicros = timeMicros / LOOPS

        if (!warmingCycle) {
            println(sprintf('Time Measure: Loops: %d, Time: %.3f ms, Avg: %.3f μs, Result: %b, Expected Result: %b, Name: %s',
                    LOOPS, timeMillis, avgMicros, result, expectedResult, testName))
        }

        return (result == expectedResult) && (warmingCycle || (maxAvgMicros <= 0) || (avgMicros <= maxAvgMicros))
    }
}
