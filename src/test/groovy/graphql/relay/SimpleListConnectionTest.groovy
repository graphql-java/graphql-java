package graphql.relay

import graphql.schema.DataFetchingEnvironmentImpl
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class SimpleListConnectionTest extends Specification {
    def "invalid list indices handled"() {
        given:
        def testList = ["a", "b"]
        def listConnection = new SimpleListConnection(testList)
        def env = new DataFetchingEnvironmentImpl(null, ["after": createCursor(3)], null, null, null, null, null, null, null, null, null);

        when:
        Object item = listConnection.get(env);

        then:
        item instanceof graphql.relay.Connection
    }


    private String createCursor(int offset) {
        def string = SimpleListConnection.DUMMY_CURSOR_PREFIX + Integer.toString(offset)
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8))
    }
}
