package graphql.relay

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.relay.SimpleListConnection
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification
import spock.lang.Unroll

class SimpleListConnectionTest extends Specification {
    def "invalid list indices handled"() {
        given:
        def testList = ["a", "b"]
        def listConnection = new SimpleListConnection(testList)
        def env = new DataFetchingEnvironment(null, ["after":createCursor(3)], null, null, null, null, null);

        when:
        Object item = listConnection.get(env);

        then:
        item instanceof graphql.relay.Connection
    }

    private static final String DUMMY_CURSOR_PREFIX = "simple-cursor";
    private String createCursor(int offset) {
        String string = graphql.relay.Base64.toBase64(DUMMY_CURSOR_PREFIX + Integer.toString(offset));
        return string;
    }
}
