package graphql.parser

import spock.lang.Specification

class ParserOptionsTest extends Specification {
    static defaultOptions = ParserOptions.getDefaultParserOptions()
    static defaultOperationOptions = ParserOptions.getDefaultOperationParserOptions()
    static defaultSdlOptions = ParserOptions.getDefaultSdlParserOptions()

    static final int ONE_MB = 1024 * 1024

    void setup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultOperationParserOptions(defaultOperationOptions)
        ParserOptions.setDefaultSdlParserOptions(defaultSdlOptions)
    }

    void cleanup() {
        ParserOptions.setDefaultParserOptions(defaultOptions)
        ParserOptions.setDefaultOperationParserOptions(defaultOperationOptions)
        ParserOptions.setDefaultSdlParserOptions(defaultSdlOptions)
    }

    def "lock in default settings"() {
        expect:
        defaultOptions.getMaxCharacters() == ONE_MB
        defaultOptions.getMaxTokens() == 15_000
        defaultOptions.getMaxWhitespaceTokens() == 200_000
        defaultOptions.isCaptureSourceLocation()
        defaultOptions.isCaptureLineComments()
        !defaultOptions.isCaptureIgnoredChars()
        defaultOptions.isReaderTrackData()

        defaultOperationOptions.getMaxTokens() == 15_000
        defaultOperationOptions.getMaxWhitespaceTokens() == 200_000
        defaultOperationOptions.isCaptureSourceLocation()
        !defaultOperationOptions.isCaptureLineComments()
        !defaultOperationOptions.isCaptureIgnoredChars()
        defaultOptions.isReaderTrackData()

        defaultSdlOptions.getMaxCharacters() == Integer.MAX_VALUE
        defaultSdlOptions.getMaxTokens() == Integer.MAX_VALUE
        defaultSdlOptions.getMaxWhitespaceTokens() == Integer.MAX_VALUE
        defaultSdlOptions.isCaptureSourceLocation()
        defaultSdlOptions.isCaptureLineComments()
        !defaultSdlOptions.isCaptureIgnoredChars()
        defaultOptions.isReaderTrackData()
    }

    def "can set in new option JVM wide"() {
        def newDefaultOptions = defaultOptions.transform({
            it.captureIgnoredChars(true)
                    .readerTrackData(false)
        }        )
        def newDefaultOperationOptions = defaultOperationOptions.transform(
                {
                    it.captureIgnoredChars(true)
                            .captureLineComments(true)
                            .maxCharacters(1_000_000)
                            .maxWhitespaceTokens(300_000)
                })
        def newDefaultSDlOptions = defaultSdlOptions.transform(
                {
                    it.captureIgnoredChars(true)
                            .captureLineComments(true)
                            .maxWhitespaceTokens(300_000)
                })

        when:
        ParserOptions.setDefaultParserOptions(newDefaultOptions)
        ParserOptions.setDefaultOperationParserOptions(newDefaultOperationOptions)
        ParserOptions.setDefaultSdlParserOptions(newDefaultSDlOptions)

        def currentDefaultOptions = ParserOptions.getDefaultParserOptions()
        def currentDefaultOperationOptions = ParserOptions.getDefaultOperationParserOptions()
        def currentDefaultSdlOptions = ParserOptions.getDefaultSdlParserOptions()

        then:

        currentDefaultOptions.getMaxCharacters() == ONE_MB
        currentDefaultOptions.getMaxTokens() == 15_000
        currentDefaultOptions.getMaxWhitespaceTokens() == 200_000
        currentDefaultOptions.isCaptureSourceLocation()
        currentDefaultOptions.isCaptureLineComments()
        currentDefaultOptions.isCaptureIgnoredChars()
        !currentDefaultOptions.isReaderTrackData()

        currentDefaultOperationOptions.getMaxCharacters() == 1_000_000
        currentDefaultOperationOptions.getMaxTokens() == 15_000
        currentDefaultOperationOptions.getMaxWhitespaceTokens() == 300_000
        currentDefaultOperationOptions.isCaptureSourceLocation()
        currentDefaultOperationOptions.isCaptureLineComments()
        currentDefaultOperationOptions.isCaptureIgnoredChars()
        currentDefaultOperationOptions.isReaderTrackData()

        currentDefaultSdlOptions.getMaxCharacters() == Integer.MAX_VALUE
        currentDefaultSdlOptions.getMaxTokens() == Integer.MAX_VALUE
        currentDefaultSdlOptions.getMaxWhitespaceTokens() == 300_000
        currentDefaultSdlOptions.isCaptureSourceLocation()
        currentDefaultSdlOptions.isCaptureLineComments()
        currentDefaultSdlOptions.isCaptureIgnoredChars()
        currentDefaultSdlOptions.isReaderTrackData()
    }
}
