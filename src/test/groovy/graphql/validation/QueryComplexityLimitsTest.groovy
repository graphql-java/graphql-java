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
        def limitsMergedFieldCount = QueryComplexityLimits.newLimitsWithDefaults()
                .maxMergedFieldCount(7)
                .build()
        def errorsMergedFieldCount = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsMergedFieldCount)

        then: "mergedFieldCount of 8 exceeds limit of 7"
        errorsMergedFieldCount.size() == 1
        errorsMergedFieldCount[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        errorsMergedFieldCount[0].message.contains("8")

        when: "the mergedFieldCount and depth limits match the query exactly"
        def limitsExact = QueryComplexityLimits.newLimitsWithDefaults()
                .maxMergedFieldCount(8)
                .maxDepth(7)
                .build()
        def errorsExact = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsExact)

        then: "validation passes at both boundaries"
        errorsExact.isEmpty()

        when: "the depth limit is below the query's depth"
        def limitsDepth = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
                .maxMergedFieldCount(3)
                .build()
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        errors[0].message.contains("4")  // foo + subFoo + moreFoos + subFoo = 4

        when: "limit of 4 should pass"
        def limitsPass = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
                .maxMergedFieldCount(6)
                .build()
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MaxQueryMergedFieldCountExceeded
        errors[0].message.contains("7")

        when: "limit of 7 should pass"
        def limitsPass = QueryComplexityLimits.newLimitsWithDefaults()
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
        def customLimits = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(5).maxMergedFieldCount(10).build()
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

    def "customizing #setting preserves the other JVM-wide limits"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()
        def defaults = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(30).maxMergedFieldCount(2_000)
                .maxFieldOccurrences(20_000).build()
        QueryComplexityLimits.setDefaultLimits(defaults)

        when:
        def builder = QueryComplexityLimits.newLimitsWithDefaults()
        builder."$setting"(123)
        def limits = builder.build()

        then:
        limits.maxDepth == depth
        limits.maxMergedFieldCount == mergedFields
        limits.maxFieldOccurrences == occurrences

        cleanup:
        QueryComplexityLimits.setDefaultLimits(originalDefault)

        where:
        setting               | depth | mergedFields | occurrences
        'maxDepth'            | 123   | 2_000        | 20_000
        'maxMergedFieldCount' | 30    | 123          | 20_000
        'maxFieldOccurrences' | 30    | 2_000        | 123
    }

    def "new builders inherit disabled JVM-wide limits"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()
        QueryComplexityLimits.setDefaultLimits(QueryComplexityLimits.NONE)

        when:
        def limits = QueryComplexityLimits.newLimitsWithDefaults().build()
        def customDepth = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(10).build()

        then:
        limits.maxDepth == Integer.MAX_VALUE
        limits.maxMergedFieldCount == Integer.MAX_VALUE
        limits.maxFieldOccurrences == Integer.MAX_VALUE
        customDepth.maxDepth == 10
        customDepth.maxMergedFieldCount == Integer.MAX_VALUE
        customDepth.maxFieldOccurrences == Integer.MAX_VALUE

        cleanup:
        QueryComplexityLimits.setDefaultLimits(originalDefault)
    }

    def "builders snapshot JVM-wide defaults at creation"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()
        def firstDefaults = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(30).maxMergedFieldCount(2_000)
                .maxFieldOccurrences(20_000).build()
        def secondDefaults = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(40).maxMergedFieldCount(3_000)
                .maxFieldOccurrences(30_000).build()
        QueryComplexityLimits.setDefaultLimits(firstDefaults)
        def existingBuilder = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(10)
        def existingLimits = existingBuilder.build()

        when:
        QueryComplexityLimits.setDefaultLimits(secondDefaults)
        def fromExistingBuilder = existingBuilder.build()
        def fromNewBuilder = QueryComplexityLimits.newLimitsWithDefaults().build()

        then:
        fromExistingBuilder.maxDepth == 10
        fromExistingBuilder.maxMergedFieldCount == 2_000
        fromExistingBuilder.maxFieldOccurrences == 20_000
        existingLimits.maxDepth == 10
        existingLimits.maxMergedFieldCount == 2_000
        existingLimits.maxFieldOccurrences == 20_000
        fromNewBuilder.maxDepth == 40
        fromNewBuilder.maxMergedFieldCount == 3_000
        fromNewBuilder.maxFieldOccurrences == 30_000
        QueryComplexityLimits.getDefaultLimits().is(secondDefaults)
        QueryComplexityLimits.DEFAULT.maxDepth == 100
        QueryComplexityLimits.DEFAULT.maxMergedFieldCount == 10_000
        QueryComplexityLimits.DEFAULT.maxFieldOccurrences == 100_000

        cleanup:
        QueryComplexityLimits.setDefaultLimits(originalDefault)
    }

    def "custom depth preserves enforcement of JVM-wide field budgets"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()
        def defaults = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(30).maxMergedFieldCount(2)
                .maxFieldOccurrences(3).build()
        QueryComplexityLimits.setDefaultLimits(defaults)
        def limits = QueryComplexityLimits.newLimitsWithDefaults().maxDepth(10).build()
        def schema = TestUtil.schema('type Query { node: Node } type Node { name: String child: Node }')
        def document = Parser.parse(query)

        expect:
        new Validator().validateDocument(schema, document, { rule -> true }, Locale.ENGLISH, limits)*.validationErrorType == errors

        cleanup:
        QueryComplexityLimits.setDefaultLimits(originalDefault)

        where:
        query                                | errors
        '{ node { name name } }'             | []
        '{ node { name name name } }'        | [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        '{ node { name child { name } } }'   | [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
        QueryComplexityLimits.newLimitsWithDefaults().maxDepth(0).build()

        then:
        thrown(IllegalArgumentException)

        when:
        QueryComplexityLimits.newLimitsWithDefaults().maxMergedFieldCount(-1).build()

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
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
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
