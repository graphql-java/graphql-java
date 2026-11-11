package graphql.validation

import graphql.TestUtil
import graphql.parser.Parser

class QueryComplexityLimitsTest extends SpecValidationBase {

    def "nested object selections have mergedFieldCount 8 and depth 7"() {
        def schema = TestUtil.schema("""
            type Query {
                foo: Foo
            }
            type Foo {
                stop : String
                bar : Bar
            }
            type Bar {
                stop : String
                foo : Foo
            }
        """)

        def query = "{ foo { bar { foo { bar { foo { stop bar { stop }}}}}}}"
        def document = new Parser().parseDocument(query)

        when: "the mergedFieldCount limit is below the query's count"
        def limitsMergedFieldCount = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(7)
                .build()
        def errorsMergedFieldCount = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsMergedFieldCount)

        then: "mergedFieldCount of 8 exceeds limit of 7"
        errorsMergedFieldCount.size() == 1
        errorsMergedFieldCount[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        errorsMergedFieldCount[0].message.contains("8")

        when: "the mergedFieldCount and depth limits match the query exactly"
        def limitsExact = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(8)
                .maxDepth(7)
                .build()
        def errorsExact = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsExact)

        then: "validation passes at both boundaries"
        errorsExact.isEmpty()

        when: "the depth limit is below the query's depth"
        def limitsDepth = QueryComplexityLimits.newLimits()
                .maxDepth(6)
                .build()
        def errorsDepth = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsDepth)

        then: "depth of 7 exceeds limit of 6"
        errorsDepth.size() == 1
        errorsDepth[0].validationErrorType == ValidationErrorType.MaxQueryDepthExceeded
        errorsDepth[0].message.contains("7")
    }

    def "fragment selections contribute to mergedFieldCount at distinct response positions"() {
        // subFoo contributes at foo/subFoo and foo/moreFoos/subFoo, giving mergedFieldCount=4.
        def schema = TestUtil.schema("""
            type Query {
                foo: Foo
            }
            type Foo {
                subFoo: String
                moreFoos: Foo
            }
        """)

        def query = "{foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }"
        def document = new Parser().parseDocument(query)

        when: "mergedFieldCount of 4 exceeds the limit of 3"
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(3)
                .build()
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        errors[0].message.contains("4")  // foo + subFoo + moreFoos + subFoo = 4

        when: "limit of 4 should pass"
        def limitsPass = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(4)
                .build()
        def errorsPass = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsPass)

        then:
        errorsPass.isEmpty()
    }

    def "aliases keep fragment selections separate in mergedFieldCount"() {
        def schema = TestUtil.schema("""
            type Query {
                foo: Foo
            }
            type Foo {
                foo: Foo
                name: String
            }
        """)

        // Each alias contributes a foo and a name; the root foo adds one, giving mergedFieldCount=7.
        def query = """
            { foo { ...F1 }}
            fragment F1 on Foo {
                a: foo { ...F2 }
                b: foo { ...F2 }
                c: foo { ...F2 }
            }
            fragment F2 on Foo {
                name
            }
        """
        def document = new Parser().parseDocument(query)

        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(6)
                .build()
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        errors[0].message.contains("7")

        when: "limit of 7 should pass"
        def limitsPass = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(7)
                .build()
        def errorsPass = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsPass)

        then:
        errorsPass.isEmpty()
    }

    def "default limits are applied automatically"() {
        expect:
        QueryComplexityLimits.DEFAULT.getMaxDepth() == 100
        QueryComplexityLimits.DEFAULT.getMaxMergedFieldCount() == 10_000
        QueryComplexityLimits.DEFAULT.getMaxFieldOccurrences() == 100_000
        QueryComplexityLimits.getDefaultLimits() == QueryComplexityLimits.DEFAULT
    }

    def "default limits can be changed globally"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()

        when: "we set custom default limits"
        def customLimits = QueryComplexityLimits.newLimits().maxDepth(5).maxMergedFieldCount(10).build()
        QueryComplexityLimits.setDefaultLimits(customLimits)

        then:
        QueryComplexityLimits.getDefaultLimits() == customLimits

        when: "we can disable limits globally with NONE"
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE)

        then:
        QueryComplexityLimits.getDefaultLimits() == QueryComplexityLimits.NONE

        cleanup:
        QueryComplexityLimits.setDefaultLimits(originalDefault)
    }

    def "customizing #setting preserves the other standard builder limits"() {
        when:
        def builder = QueryComplexityLimits.newLimits()
        builder."$setting"(123)
        def limits = builder.build()

        then:
        limits.maxDepth == depth
        limits.maxMergedFieldCount == mergedFields
        limits.maxFieldOccurrences == occurrences

        where:
        setting               | depth | mergedFields | occurrences
        'maxDepth'            | 123   | 10_000       | 100_000
        'maxMergedFieldCount' | 100   | 123          | 100_000
        'maxFieldOccurrences' | 100   | 10_000       | 123
    }

    def "new builders retain standard protections when global limits are disabled"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE)

        when:
        def limits = QueryComplexityLimits.newLimits().build()

        then:
        limits.maxDepth == QueryComplexityLimits.DEFAULT.maxDepth
        limits.maxMergedFieldCount == QueryComplexityLimits.DEFAULT.maxMergedFieldCount
        limits.maxFieldOccurrences == QueryComplexityLimits.DEFAULT.maxFieldOccurrences

        cleanup:
        QueryComplexityLimits.setDefaultLimits(originalDefault)
    }

    def "simple queries pass with default limits"() {
        def query = """
            query deepQuery {
                dog {
                    name
                    owner {
                        name
                    }
                }
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def "NONE disables limits entirely"() {
        def schema = TestUtil.schema("""
            type Query { a: A }
            type A { b: B }
            type B { c: C }
            type C { d: String }
        """)
        def query = "{ a { b { c { d }}}}"
        def document = new Parser().parseDocument(query)

        when: "using NONE, no limits are enforced"
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, QueryComplexityLimits.NONE)

        then:
        errors.isEmpty()
    }

    def "mergedFieldCount limit is enforced"() {
        def query = """
            query {
                dog {
                    name
                    nickname
                    barkVolume
                }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(3)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        validationErrors[0].message.contains("4")  // actual
        validationErrors[0].message.contains("3")  // limit
    }

    def "depth limit is enforced"() {
        def query = """
            query {
                dog {
                    owner {
                        name
                    }
                }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxDepth(2)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryDepthExceeded
        validationErrors[0].message.contains("3")  // actual depth
        validationErrors[0].message.contains("2")  // limit
    }

    def "fragment selections contribute to mergedFieldCount under each alias"() {
        // Three dog aliases each contribute a dog, name and nickname, giving mergedFieldCount=9.
        def query = """
            fragment F on Dog { name nickname }
            query {
                dog1: dog { ...F }
                dog2: dog { ...F }
                dog3: dog { ...F }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(8)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        validationErrors[0].message.contains("9")  // actual
        validationErrors[0].message.contains("8")  // limit
    }

    def "fragment depth adds to current depth"() {
        // Query depth: dog at depth 1, fragment adds 1 more (name) = max depth 2
        def query = """
            fragment F on Dog { name }
            query {
                dog { ...F }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxDepth(1)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryDepthExceeded
    }

    def "nested fragments are handled correctly"() {
        // dog, A's name and B's nickname give mergedFieldCount=3.
        def query = """
            fragment A on Dog { name ...B }
            fragment B on Dog { nickname }
            query {
                dog { ...A }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(2)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
    }

    def "multiple operations each have separate limits"() {
        // Each operation should be validated independently
        def query = """
            query First {
                dog { name }
            }
            query Second {
                dog { name nickname barkVolume }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(3)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        // The second operation has mergedFieldCount=4 (dog + 3 scalar fields), exceeding the limit of 3.
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
    }

    def "inline fragment selections contribute to mergedFieldCount"() {
        def query = """
            query {
                dog {
                    ... on Dog {
                        name
                        nickname
                    }
                }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(2)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        // dog, name and nickname give mergedFieldCount=3.
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
    }

    def "passes when within limits"() {
        def query = """
            query {
                dog {
                    name
                    owner {
                        name
                    }
                }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(10)
                .maxDepth(5)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.isEmpty()
    }

    def "QueryComplexityLimits.NONE has no limits"() {
        expect:
        QueryComplexityLimits.NONE.getMaxDepth() == Integer.MAX_VALUE
        QueryComplexityLimits.NONE.getMaxMergedFieldCount() == Integer.MAX_VALUE
        QueryComplexityLimits.NONE.getMaxFieldOccurrences() == Integer.MAX_VALUE
    }

    def "builder validates positive values"() {
        when:
        QueryComplexityLimits.newLimits().maxDepth(0).build()

        then:
        thrown(IllegalArgumentException)

        when:
        QueryComplexityLimits.newLimits().maxMergedFieldCount(-1).build()

        then:
        thrown(IllegalArgumentException)
    }

    def "cyclic fragments don't cause infinite loop in complexity calculation"() {
        // This query has a cycle: A -> B -> A
        // The validation should detect the cycle error, but complexity calculation shouldn't hang
        def query = """
            fragment A on Dog { ...B }
            fragment B on Dog { ...A }
            query {
                dog { ...A }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxMergedFieldCount(100)
                .maxDepth(100)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        // Should get fragment cycle error, not hang
        validationErrors.any { it.validationErrorType == ValidationErrorType.FragmentCycle }
    }
}
