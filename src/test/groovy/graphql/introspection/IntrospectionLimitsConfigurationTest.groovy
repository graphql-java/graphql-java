package graphql.introspection

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.parser.Parser
import graphql.validation.GoodFaithIntrospectionExceeded
import graphql.validation.QueryComplexityLimits
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import java.util.function.Predicate

class IntrospectionLimitsConfigurationTest extends Specification {
    def schema = TestUtil.schema('type Query { node: Node } type Node { name: String child: Node }')
    def graphql = GraphQL.newGraphQL(schema).build()
    def originalLimits = QueryComplexityLimits.getDefaultLimits()

    def cleanup() {
        QueryComplexityLimits.setDefaultLimits(originalLimits)
    }

    def "request enablement #requestEnabled overrides JVM enablement #globallyEnabled for execution and direct validation"() {
        given:
        def query = '{ a: __type(name: "Query") { name } b: __type(name: "Query") { name } }'
        def document = Parser.parse(query)
        def defaults = QueryComplexityLimits.DEFAULT.transform { it.introspectionLimitsEnabled(globallyEnabled) }
        QueryComplexityLimits.setDefaultLimits(defaults)
        def limits = QueryComplexityLimits.newLimitsWithDefaults().introspectionLimitsEnabled(requestEnabled).build()

        when:
        def result = execute(query, limits)

        then:
        result.errors*.class == (requestEnabled ? [BadFaithIntrospectionError] : [])
        QueryComplexityLimits.getDefaultLimits().is(defaults)
        graphql.execute(query).errors*.class == (globallyEnabled ? [BadFaithIntrospectionError] : [])

        when:
        def directError = directFailure(document, limits)

        then:
        (directError != null) == requestEnabled
        directError?.toBadFaithError()?.message == result.errors.find()?.message
        (directFailure(document, null) != null) == globallyEnabled

        where:
        globallyEnabled | requestEnabled
        true            | true
        true            | false
        false           | true
        false           | false
    }

    def "parseAndValidate uses request limits and preserves the direct-validation exception"() {
        given:
        QueryComplexityLimits.setDefaultLimits(defaults)
        def query = '{ a: __type(name: "Query") { name } b: __type(name: "Query") { name } }'
        def disabled = QueryComplexityLimits.DEFAULT.transform { it.introspectionLimitsEnabled(false) }
        def input = ExecutionInput.newExecutionInput(query)
                .graphQLContext([(QueryComplexityLimits.KEY): disabled]).build()

        when:
        def result = ParseAndValidate.parseAndValidate(schema, input)

        then:
        !result.isFailure()

        when:
        def occurrenceLimited = disabled.transform { it.maxFieldOccurrences(1) }
        def occurrenceInput = ExecutionInput.newExecutionInput(query)
                .graphQLContext([(QueryComplexityLimits.KEY): occurrenceLimited]).build()
        result = ParseAndValidate.parseAndValidate(schema, occurrenceInput)

        then:
        result.validationErrors*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]

        when:
        def enabledInput = ExecutionInput.newExecutionInput(query)
                .graphQLContext([(QueryComplexityLimits.KEY): QueryComplexityLimits.DEFAULT]).build()
        ParseAndValidate.parseAndValidate(schema, enabledInput)

        then:
        thrown(GoodFaithIntrospectionExceeded)

        where:
        defaults << [QueryComplexityLimits.DEFAULT, QueryComplexityLimits.NONE]
    }

    def "configurable introspection caps can exceed the previous fixed limits"() {
        given:
        def limits = QueryComplexityLimits.DEFAULT.transform {
            it.maxIntrospectionMergedFieldCount(fields).maxIntrospectionDepth(depth)
        }
        def tighter = limits.transform { it."$setting"(limit - 1) }
        def document = Parser.parse(query)

        expect:
        new Validator().validateDocument(schema, document, { true }, Locale.ENGLISH, limits).empty
        execute(query, limits).errors.empty
        directFailure(document, tighter) != null
        execute(query, tighter).errors*.class == [BadFaithIntrospectionError]

        where:
        query                                                                                                 | fields | depth | setting                            | limit
        '{ __type(name: "Query") { ' + (1..500).collect { "n$it: name" }.join(' ') + ' } }'                   | 501    | 2     | 'maxIntrospectionMergedFieldCount' | 501
        '{ __type(name: "Query") { ' + 'ofType { ' * 19 + 'name' + ' }' * 19 + ' } }'                          | 21     | 21    | 'maxIntrospectionDepth'            | 21
    }

    def "special errors remain unchanged for #violation with validation rules enabled #rulesEnabled"() {
        given:
        def limits = QueryComplexityLimits.DEFAULT.transform {
            it.maxMergedFieldCount(generalFields).maxDepth(generalDepth)
                    .maxIntrospectionMergedFieldCount(introFields).maxIntrospectionDepth(introDepth)
        }
        def predicate = { rule -> rulesEnabled }

        when:
        new Validator().validateDocument(schema, Parser.parse(query), predicate, Locale.ENGLISH, limits)

        then:
        def exception = thrown(GoodFaithIntrospectionExceeded)
        exception.toBadFaithError().message == message

        when:
        def result = execute(query, limits, rulesEnabled)

        then:
        result.errors.size() == 1
        result.errors[0] instanceof BadFaithIntrospectionError
        result.errors[0].message == message
        result.errors[0].errorType.toString() == 'BadFaithIntrospection'
        result.errors[0].locations == null
        result.errors[0].toSpecification() == [message: message, extensions: [classification: 'BadFaithIntrospection']]
        result.data == null

        where:
        [violation, rulesEnabled] << [['introspection fields', 'introspection depth', 'general fields', 'general depth', 'restricted coordinate', 'mixed operation'], [true, false]].combinations()
        generalFields = violation == 'general fields' ? 3 : 10_000
        generalDepth = violation == 'general depth' ? 2 : 100
        introFields = violation in ['introspection fields', 'mixed operation'] ? 3 : 500
        introDepth = violation == 'introspection depth' ? 2 : 20
        query = violation == 'restricted coordinate' ? '{ __schema { types { a: fields { name } b: fields { name } } } }' :
                violation == 'mixed operation' ? '{ __type(name: "Query") { name } node { name } }' :
                        '{ __type(name: "Query") { name ofType { name } } }'
        detail = violation == 'restricted coordinate' ? '__Type.fields is present too often!' :
                violation in ['introspection depth', 'general depth'] ? 'the query is too big: Query depth 3 exceeds maximum allowed depth 2' :
                        'the query is too big: Query has 4 merged fields, exceeding the maximum allowed 3'
        message = 'This request is not asking for introspection in good faith - ' + detail
    }

    def "NONE disables introspection caps and restricted coordinates in direct validation and execution"() {
        given:
        def query = '{ a: __type(name: "Query") { ' + (1..501).collect { "n$it: name" }.join(' ') +
                ' ofType { ' * 21 + 'name' + ' }' * 21 + ' } b: __type(name: "Query") { name } }'
        def limits = QueryComplexityLimits.NONE

        expect:
        new Validator().validateDocument(schema, Parser.parse(query), { true }, Locale.ENGLISH, limits).empty
        execute(query, limits).errors.empty

        when:
        QueryComplexityLimits.setDefaultLimits(limits)

        then:
        new Validator().validateDocument(schema, Parser.parse(query), Locale.ENGLISH).empty
        graphql.execute(query).errors.empty
    }

    def "disabling introspection protections leaves general #setting active"() {
        given:
        def limits = QueryComplexityLimits.DEFAULT.transform {
            it.introspectionLimitsEnabled(false)."$setting"(1)
        }
        def query = '{ __type(name: "Query") { name } }'

        expect:
        new Validator().validateDocument(schema, Parser.parse(query), { true }, Locale.ENGLISH, limits)*.validationErrorType == [error]
        execute(query, limits).errors*.validationErrorType == [error]

        where:
        setting               | error
        'maxMergedFieldCount' | ValidationErrorType.MaxQueryMergedFieldCountExceeded
        'maxDepth'            | ValidationErrorType.MaxQueryDepthExceeded
        'maxFieldOccurrences' | ValidationErrorType.MaxQueryFieldOccurrencesExceeded
    }

    def "occurrence failures remain ordinary validation errors with introspection enabled"() {
        given:
        def limits = QueryComplexityLimits.DEFAULT.transform { it.maxFieldOccurrences(3) }
        def query = '{ __type(name: "Query") { name name name } }'

        expect:
        new Validator().validateDocument(schema, Parser.parse(query), { false }, Locale.ENGLISH, limits)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        execute(query, limits, false).errors*.validationErrorType == [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
    }

    def "ordinary selections and typename alone do not activate introspection caps"() {
        given:
        def limits = QueryComplexityLimits.DEFAULT.transform {
            it.maxIntrospectionMergedFieldCount(1).maxIntrospectionDepth(1)
        }
        def query = '{ __typename node { name child { name } } }'

        expect:
        new Validator().validateDocument(schema, Parser.parse(query), { true }, Locale.ENGLISH, limits).empty
        execute(query, limits).errors.empty
    }

    private def execute(String query, QueryComplexityLimits limits, boolean rulesEnabled = true) {
        def input = ExecutionInput.newExecutionInput(query).graphQLContext([
                (QueryComplexityLimits.KEY): limits,
                (ParseAndValidate.INTERNAL_VALIDATION_PREDICATE_HINT): { rule -> rulesEnabled } as Predicate
        ]).build()
        graphql.execute(input)
    }

    private GoodFaithIntrospectionExceeded directFailure(def document, QueryComplexityLimits limits) {
        try {
            def errors = new Validator().validateDocument(schema, document, { true }, Locale.ENGLISH, limits)
            assert errors.empty
            return null
        } catch (GoodFaithIntrospectionExceeded exception) {
            return exception
        }
    }
}
