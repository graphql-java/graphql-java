package graphql.parser

import spock.lang.Specification

class ParserOptionsTest extends Specification {
    static defaultOptions = ParserOptions.getDefaultParserOptions()
    static defaultOperationOptions = ParserOptions.getDefaultOperationParserOptions()

    void setup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultOperationParserOptions(defaultOperationOptions)
    }

    void cleanup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultOperationParserOptions(defaultOperationOptions)
    }

    def "lock in default settings"() {
        expect:
        defaultOptions.getMaxTokens() == 15_000
        defaultOptions.getMaxWhitespaceTokens() == 200_000
        defaultOptions.isCaptureSourceLocation()
        defaultOptions.isCaptureLineComments()
        !defaultOptions.isCaptureIgnoredChars()

        defaultOperationOptions.getMaxTokens() == 15_000
        defaultOperationOptions.getMaxWhitespaceTokens() == 200_000
        defaultOperationOptions.isCaptureSourceLocation()
        !defaultOperationOptions.isCaptureLineComments()
        !defaultOperationOptions.isCaptureIgnoredChars()
    }

    def "can set in new option JVM wide"() {
        def newDefaultOptions = defaultOptions.transform({ it.captureIgnoredChars(true) })
        def newDefaultOperationOptions = defaultOperationOptions.transform(
                { it.captureIgnoredChars(true).captureLineComments(true).maxWhitespaceTokens(300_000) })

        when:
        ParserOptions.setDefaultParserOptions(newDefaultOptions)
        ParserOptions.setDefaultOperationParserOptions(newDefaultOperationOptions)

        def currentDefaultOptions = ParserOptions.getDefaultParserOptions()
        def currentDefaultOperationOptions = ParserOptions.getDefaultOperationParserOptions()

        then:

        currentDefaultOptions.getMaxTokens() == 15_000
        currentDefaultOptions.getMaxWhitespaceTokens() == 200_000
        currentDefaultOptions.isCaptureSourceLocation()
        currentDefaultOptions.isCaptureLineComments()
        currentDefaultOptions.isCaptureIgnoredChars()

        currentDefaultOperationOptions.getMaxTokens() == 15_000
        currentDefaultOperationOptions.getMaxWhitespaceTokens() == 300_000
        currentDefaultOperationOptions.isCaptureSourceLocation()
        currentDefaultOperationOptions.isCaptureLineComments()
        currentDefaultOperationOptions.isCaptureIgnoredChars()
    }
}
