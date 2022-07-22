package graphql.parser

import spock.lang.Specification

class ParserOptionsTest extends Specification {
    static defaultOptions = ParserOptions.getDefaultParserOptions()
    static defaultQueryOptions = ParserOptions.getDefaultQueryParserOptions()

    void setup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultQueryParserOptions(defaultQueryOptions)
    }

    void cleanup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultQueryParserOptions(defaultQueryOptions)
    }

    def "lock in default settings"() {
        expect:
        defaultOptions.getMaxTokens() == 15_000
        defaultOptions.isCaptureSourceLocation()
        defaultOptions.isCaptureLineComments()
        !defaultOptions.isCaptureIgnoredChars()

        defaultQueryOptions.getMaxTokens() == 15_000
        defaultQueryOptions.isCaptureSourceLocation()
        !defaultQueryOptions.isCaptureLineComments()
        !defaultQueryOptions.isCaptureIgnoredChars()
    }

    def "can set in new option JVM wide"() {
        def newDefaultOptions = defaultOptions.transform({ it.captureIgnoredChars(true) })
        def newDefaultQueryOptions = defaultQueryOptions.transform({ it.captureIgnoredChars(true).captureLineComments(true) })

        when:
        ParserOptions.setDefaultParserOptions(newDefaultOptions)
        ParserOptions.setDefaultQueryParserOptions(newDefaultQueryOptions)

        def currentDefaultOptions = ParserOptions.getDefaultParserOptions()
        def currentDefaultQueryOptions = ParserOptions.getDefaultQueryParserOptions()

        then:

        currentDefaultOptions.getMaxTokens() == 15_000
        currentDefaultOptions.isCaptureSourceLocation()
        currentDefaultOptions.isCaptureLineComments()
        currentDefaultOptions.isCaptureIgnoredChars()

        currentDefaultQueryOptions.getMaxTokens() == 15_000
        currentDefaultQueryOptions.isCaptureSourceLocation()
        currentDefaultQueryOptions.isCaptureLineComments()
        currentDefaultQueryOptions.isCaptureIgnoredChars()

    }
}
