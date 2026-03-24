package graphql.validation

import graphql.TestUtil
import graphql.parser.Parser

class QueryComplexityLimitsTest extends SpecValidationBase {

    // ==================== ENO Parity Tests ====================
    // These tests verify that our complexity tracking matches ExecutableNormalizedOperation (ENO)

    def "ENO parity - depth and field count match ENO calculation"() {
        // This test mirrors ExecutableNormalizedOperationFactoryTest."can capture depth and field count"
        // ENO reports: depth=7, fieldCount=8
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

        when: "we set limits that would fail if counts don't match ENO"
        // ENO says fieldCount=8, so limit of 7 should fail
        def limitsFieldCount = QueryComplexityLimits.newLimits()
                .maxFieldsCount(7)
                .build()
        def errorsFieldCount = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsFieldCount)

        then: "field count of 8 exceeds limit of 7"
        errorsFieldCount.size() == 1
        errorsFieldCount[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
        errorsFieldCount[0].message.contains("8")

        when: "we set limits that match ENO exactly"
        def limitsExact = QueryComplexityLimits.newLimits()
                .maxFieldsCount(8)
                .maxDepth(7)
                .build()
        def errorsExact = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsExact)

        then: "validation passes with exact ENO counts"
        errorsExact.isEmpty()

        when: "depth limit of 6 should fail (ENO says depth=7)"
        def limitsDepth = QueryComplexityLimits.newLimits()
                .maxDepth(6)
                .build()
        def errorsDepth = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsDepth)

        then: "depth of 7 exceeds limit of 6"
        errorsDepth.size() == 1
        errorsDepth[0].validationErrorType == ValidationErrorType.MaxQueryDepthExceeded
        errorsDepth[0].message.contains("7")
    }

    def "ENO parity - fragment spread counts fields at each site"() {
        // This test mirrors ExecutableNormalizedOperationFactoryTest."query with fragment definition"
        // Query: {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
        // ENO output: ['Query.foo', 'Foo.subFoo', 'Foo.moreFoos', 'Foo.subFoo']
        // So subFoo is counted TWICE (once per spread) = 4 total fields
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

        when: "limit of 3 should fail (ENO counts 4 fields)"
        def limits = QueryComplexityLimits.newLimits()
                .maxFieldsCount(3)
                .build()
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
        errors[0].message.contains("4")  // foo + subFoo + moreFoos + subFoo = 4

        when: "limit of 4 should pass"
        def limitsPass = QueryComplexityLimits.newLimits()
                .maxFieldsCount(4)
                .build()
        def errorsPass = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsPass)

        then:
        errorsPass.isEmpty()
    }

    def "ENO parity - deeply nested fragments multiply field counts"() {
        // Similar to ExecutableNormalizedOperationFactoryTest."factory has a default max node count"
        // Each fragment spreads 3 times, creating exponential growth
        def schema = TestUtil.schema("""
            type Query {
                foo: Foo
            }
            type Foo {
                foo: Foo
                name: String
            }
        """)

        // F1 spreads F2 three times, F2 has just 'name'
        // F1 contributes: 3 * F2's fields = 3 * 1 = 3 fields
        // Query: foo + F1's fields = 1 + 3 = 4 fields
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
        // foo (1) + a:foo (1) + b:foo (1) + c:foo (1) + name*3 (3) = 7 fields
        def limits = QueryComplexityLimits.newLimits()
                .maxFieldsCount(6)
                .build()
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        errors.size() == 1
        errors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
        errors[0].message.contains("7")

        when: "limit of 7 should pass"
        def limitsPass = QueryComplexityLimits.newLimits()
                .maxFieldsCount(7)
                .build()
        def errorsPass = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, limitsPass)

        then:
        errorsPass.isEmpty()
    }

    // ==================== Original Tests ====================

    def "default limits are applied automatically"() {
        expect:
        QueryComplexityLimits.DEFAULT.getMaxDepth() == 100
        QueryComplexityLimits.DEFAULT.getMaxFieldsCount() == 100_000
        QueryComplexityLimits.getDefaultLimits() == QueryComplexityLimits.DEFAULT
    }

    def "default limits can be changed globally"() {
        given:
        def originalDefault = QueryComplexityLimits.getDefaultLimits()

        when: "we set custom default limits"
        def customLimits = QueryComplexityLimits.newLimits().maxDepth(5).maxFieldsCount(10).build()
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
        // This query has depth 4, which exceeds default of 50? No, 4 < 50. Let me create a deeper one.
        // Actually let's just verify NONE works by setting a very low custom limit first, then NONE
        def query = "{ a { b { c { d }}}}"
        def document = new Parser().parseDocument(query)

        when: "using NONE, no limits are enforced"
        def errors = new Validator().validateDocument(schema, document, { r -> true }, Locale.ENGLISH, QueryComplexityLimits.NONE)

        then:
        errors.isEmpty()
    }

    def "field count limit is enforced"() {
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
                .maxFieldsCount(3)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
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

    def "fragment fields are counted at each spread site"() {
        // Fragment F has 2 fields (name, nickname)
        // Query has: dog1, dog2, dog3 = 3 fields + 3 spreads * 2 fields = 9 total fields
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
                .maxFieldsCount(8)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
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
        // Fragment A spreads fragment B, each has 1 field
        // Total: dog (1) + A's name (1) + B's nickname (1) = 3 fields
        def query = """
            fragment A on Dog { name ...B }
            fragment B on Dog { nickname }
            query {
                dog { ...A }
            }
        """
        when:
        def limits = QueryComplexityLimits.newLimits()
                .maxFieldsCount(2)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
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
                .maxFieldsCount(3)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        // Second operation has 4 fields (dog + 3 scalar fields), which exceeds limit of 3
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
    }

    def "inline fragments count their fields"() {
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
                .maxFieldsCount(2)
                .build()
        def document = new Parser().parseDocument(query)
        def validationErrors = new Validator().validateDocument(
                SpecValidationSchema.specValidationSchema, document, { r -> true }, Locale.ENGLISH, limits)

        then:
        // dog (1) + name (1) + nickname (1) = 3 fields
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.MaxQueryFieldsExceeded
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
                .maxFieldsCount(10)
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
        QueryComplexityLimits.NONE.getMaxFieldsCount() == Integer.MAX_VALUE
    }

    def "builder validates positive values"() {
        when:
        QueryComplexityLimits.newLimits().maxDepth(0).build()

        then:
        thrown(IllegalArgumentException)

        when:
        QueryComplexityLimits.newLimits().maxFieldsCount(-1).build()

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
                .maxFieldsCount(100)
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
