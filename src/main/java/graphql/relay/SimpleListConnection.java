package graphql.relay;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;

public class SimpleListConnection implements DataFetcher {

    private static final String DUMMY_CURSOR_PREFIX = "simple-cursor";
    private final String prefix;
    private final List<?> data;

    public SimpleListConnection(List<?> data, String prefix) {
        if (prefix == null || prefix.length() == 0) {
            throw new IllegalArgumentException("prefix cannot be null or empty");
        }
        this.prefix = prefix;
        this.data = data;
    }

    public SimpleListConnection(List<?> data) {
        this(data, DUMMY_CURSOR_PREFIX);
    }

    private List<Edge> buildEdges() {
        List<Edge> edges = new ArrayList<>();
        int ix = 0;
        for (Object object : data) {
            edges.add(new DefaultEdge(object, new DefaultConnectionCursor(createCursor(ix++))));
        }
        return edges;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {

        List<Edge> edges = buildEdges();

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

        if (edges.size() == 0) {
            return emptyConnection();
        }

        Edge firstEdge = edges.get(0);
        Edge lastEdge = edges.get(edges.size() - 1);

        DefaultPageInfo pageInfo = new DefaultPageInfo();
        pageInfo.setStartCursor(firstEdge.getCursor());
        pageInfo.setEndCursor(lastEdge.getCursor());
        pageInfo.setHasPreviousPage(!firstEdge.getCursor().equals(firstPresliceCursor));
        pageInfo.setHasNextPage(!lastEdge.getCursor().equals(lastPresliceCursor));

        DefaultConnection connection = new DefaultConnection();
        connection.setEdges(edges);
        connection.setPageInfo(pageInfo);

        return connection;
    }

    private Connection emptyConnection() {
        DefaultConnection connection = new DefaultConnection();
        connection.setPageInfo(new DefaultPageInfo());
        return connection;
    }

    public ConnectionCursor cursorForObjectInConnection(Object object) {
        int index = data.indexOf(object);
        String cursor = createCursor(index);
        return new DefaultConnectionCursor(cursor);
    }

    private int getOffsetFromCursor(String cursor, int defaultValue) {
        if (cursor == null) {
            return defaultValue;
        }
        String string = new String(getDecoder().decode(cursor), StandardCharsets.UTF_8);
        return Integer.parseInt(string.substring(prefix.length()));
    }

    private String createCursor(int offset) {
        byte[] bytes = (prefix + Integer.toString(offset)).getBytes(StandardCharsets.UTF_8);
        return getEncoder().encodeToString(bytes);
    }
}