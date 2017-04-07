package graphql.relay;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpleListConnection<T> implements DataFetcher<Connection<T>> {

    private static final String DUMMY_CURSOR_PREFIX = "simple-cursor";
    private final String prefix;
    private final List<T> data;

    public SimpleListConnection(List<T> data, String prefix) {
        if (prefix == null || prefix.length() == 0) {
            throw new IllegalArgumentException("prefix cannot be null or empty");
        }
        this.prefix = prefix;
        this.data = data;
    }

    public SimpleListConnection(List<T> data) {
        this(data, DUMMY_CURSOR_PREFIX);
    }

    private List<Edge<T>> buildEdges() {
        List<Edge<T>> edges = new ArrayList<>();
        int ix = 0;
        for (T object : data) {
            edges.add(new DefaultEdge<>(object, new DefaultConnectionCursor(createCursor(ix++))));
        }
        return edges;
    }

    @Override
    public Connection<T> get(DataFetchingEnvironment environment) {

        List<Edge<T>> edges = buildEdges();

        int afterOffset = getOffsetFromCursor(environment.<String>getArgument("after"), -1);
        int begin = Math.max(afterOffset, -1) + 1;
        int beforeOffset = getOffsetFromCursor(environment.<String>getArgument("before"), edges.size());
        int end = Math.min(beforeOffset, edges.size());

        edges = edges.subList(begin, end);
        if (edges.size() == 0) {
            return emptyConnection();
        }

        Integer first = environment.<Integer>getArgument("first");
        Integer last = environment.<Integer>getArgument("last");

        ConnectionCursor firstPresliceCursor = edges.get(0).getCursor();
        ConnectionCursor lastPresliceCursor = edges.get(edges.size() - 1).getCursor();

        if (first != null) {
            edges = edges.subList(0, first <= edges.size() ? first : edges.size());
        }
        if (last != null) {
            edges = edges.subList(last > edges.size() ? 0 : edges.size() - last, edges.size());
        }

        if (edges.isEmpty()) {
            return emptyConnection();
        }

        Edge<T> firstEdge = edges.get(0);
        Edge<T> lastEdge = edges.get(edges.size() - 1);

        PageInfo pageInfo = new DefaultPageInfo(
            firstEdge.getCursor(),
            lastEdge.getCursor(),
            !firstEdge.getCursor().equals(firstPresliceCursor),
            !lastEdge.getCursor().equals(lastPresliceCursor)
        );

        return new DefaultConnection<T>(
            edges,
            pageInfo
        );
    }

    private Connection<T> emptyConnection() {
        PageInfo pageInfo = new DefaultPageInfo(null, null, false, false);
        return new DefaultConnection<>(Collections.emptyList(), pageInfo);
    }

    /**
     * find the object's cursor, or null if the object is not in this connection.
     */
    public ConnectionCursor cursorForObjectInConnection(T object) {
        int index = data.indexOf(object);
        if (index == -1) {
            return null;
        }
        String cursor = createCursor(index);
        return new DefaultConnectionCursor(cursor);
    }

    private int getOffsetFromCursor(String cursor, int defaultValue) {
        if (cursor == null) {
            return defaultValue;
        }
        String string = Base64.fromBase64(cursor);
        return Integer.parseInt(string.substring(prefix.length()));
    }

    private String createCursor(int offset) {
        return Base64.toBase64(prefix + Integer.toString(offset));
    }
}