package graphql.validation

import graphql.TestUtil
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class FragmentExpansionComplexityTest extends Specification {
    def schema = TestUtil.schema('''
        type Query { node: Node }
        type Node { name: String child: Node }
    ''')

    def "F to G to J counts fields at every level but fragment spreads do not add depth"() {
        given:
        def query = '''
            { node { ...F } }
            fragment F on Node { name ...G }
            fragment G on Node { child { ...J } }
            fragment J on Node { name }
        '''

        expect:
        // Expanded selection: node { name child { name } }.
        assertComplexityBounds(query, 4, 4, 3)
    }

    def "repeating F G and J multiplies occurrences while their response fields merge"() {
        given:
        def query = '''
            { node { ...F ...F } }
            fragment F on Node { name ...G ...G }
            fragment G on Node { child { ...J ...J } }
            fragment J on Node { name }
        '''

        expect:
        // J contributes 1 occurrence; G contributes 1 + 2 = 3; F contributes 1 + 2 * 3 = 7.
        // The operation has 1 + 2 * 7 = 15 occurrences, but still merges to node { name child { name } }.
        assertComplexityBounds(query, 4, 15, 3)
    }

    def "diamond expansion counts shared J at every use in branch order #branches"() {
        given:
        def query = """
            { node { ...F } }
            fragment F on Node { $branches }
            fragment G on Node { name ...J }
            fragment H on Node { child { ...J } ...J }
            fragment J on Node { name name }
        """

        expect:
        // G contributes 3 occurrences and H contributes 5, including J at two response positions.
        // The operation has 1 + 3 + 5 = 9 occurrences and merges to node { name child { name } }.
        assertComplexityBounds(query, 4, 9, 3)

        where:
        branches << ['...G ...H', '...H ...G']
    }

    def "shared F to G to J counts stay per operation with reversed operations #reverseOperations and definitions #reverseDefinitions"() {
        given:
        def first = 'query First { node { ...F } }'
        def second = 'query Second { node { child { ...F ...F } } }'
        def fragments = [
                'fragment F on Node { name ...G }',
                'fragment G on Node { child { ...J } }',
                'fragment J on Node { name }',
        ]
        def operations = reverseOperations ? second + first : first + second
        def definitions = reverseDefinitions ? fragments.reverse() : fragments
        def query = operations + definitions.join('\n')

        expect:
        // First has 4 merged fields, 4 occurrences and depth 3.
        // Second has 5 merged fields, 2 + 2 * 3 = 8 occurrences and depth 4.
        // Limits apply to each operation, not the sum; cached relative depth must fit either position.
        assertComplexityBounds(query, 5, 8, 4)

        where:
        reverseOperations | reverseDefinitions
        false             | false
        false             | true
        true              | false
        true              | true
    }

    private void assertComplexityBounds(String query, int mergedFields, int occurrences, int depth) {
        def document = Parser.parse(query)
        assert validate(document, mergedFields, occurrences, depth).empty
        assert validate(document, mergedFields - 1, occurrences, depth)*.validationErrorType ==
                [ValidationErrorType.MaxQueryMergedFieldCountExceeded]
        assert validate(document, mergedFields, occurrences - 1, depth)*.validationErrorType ==
                [ValidationErrorType.MaxQueryFieldOccurrencesExceeded]
        assert validate(document, mergedFields, occurrences, depth - 1)*.validationErrorType ==
                [ValidationErrorType.MaxQueryDepthExceeded]
    }

    private List<ValidationError> validate(Document document, int mergedFields, int occurrences, int depth) {
        def limits = QueryComplexityLimits.newLimitsWithDefaults()
                .maxMergedFieldCount(mergedFields)
                .maxFieldOccurrences(occurrences)
                .maxDepth(depth)
                .build()
        new Validator().validateDocument(schema, document, { rule -> true }, Locale.ENGLISH, limits)
    }
}
