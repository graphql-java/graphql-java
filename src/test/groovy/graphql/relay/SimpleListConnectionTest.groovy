package graphql.relay

import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.nio.charset.StandardCharsets

import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment

class SimpleListConnectionTest extends Specification {

    DataFetchingEnvironment afterCursorEnv(String cursor) {
        newDataFetchingEnvironment().arguments(["after": cursor]).build()
    }

    def createCursor(int offset) {
        def string = SimpleListConnection.DUMMY_CURSOR_PREFIX + Integer.toString(offset)
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8))
    }

    def createNonOffsetCursor(String s) {
        def string = SimpleListConnection.DUMMY_CURSOR_PREFIX + s
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8))
    }

    def "invalid list indices handled"() {
        given:
        def testList = ["a", "b"]
        def listConnection = new SimpleListConnection(testList)

        def env = afterCursorEnv(createCursor(3))

        when:
        Object item = listConnection.get(env)

        then:
        item instanceof Connection
    }


    def "invalid cursor throws exception"() {
        given:
        def listConnection = new SimpleListConnection(["a", "b"])

        when:
        listConnection.get(afterCursorEnv("04/29/2017"))

        then:
        thrown(InvalidCursorException)

        when:
        listConnection.get(afterCursorEnv("not-base64"))

        then:
        thrown(InvalidCursorException)

        when:
        listConnection.get(afterCursorEnv(createNonOffsetCursor("base64 but not an integer offset")))

        then:
        thrown(InvalidCursorException)

        when:
        listConnection.get(afterCursorEnv(createCursor(1)))

        then:
        notThrown(InvalidCursorException)

    }
}
