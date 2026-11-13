package graphql.parser

import spock.lang.Specification

class SafeTokenReaderTest extends Specification {

    def "will count how many its read and stop after max"() {
        when:
        StringReader sr = new StringReader("0123456789")
        SafeTokenReader safeReader = new SafeTokenReader(sr, 5,
                { Integer maxChars -> throw new RuntimeException("max " + maxChars) })
        safeReader.readLine()

        then:
        def rte = thrown(RuntimeException)
        rte.message == "max 5"
    }
}
