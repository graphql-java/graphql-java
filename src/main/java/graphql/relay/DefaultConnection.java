package graphql.relay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultConnection<T> implements Connection<T> {

    private List<Edge<T>> edges = new ArrayList<>();

    private PageInfo pageInfo;

    /**
     * @deprecated prefer {@link #DefaultConnection(List, PageInfo)}
     */
    @Deprecated
    public DefaultConnection() {
    }

    /**
     * @param edges edges
     * @param pageInfo page info
     * @throws IllegalArgumentException if edges or page info is null. use {@link Collections#emptyList()} for empty edges.
     */
    public DefaultConnection(List<Edge<T>> edges, PageInfo pageInfo) {
        if (edges == null) {
            throw new IllegalArgumentException("edges cannot be empty");
        }
        if (pageInfo == null) {
            throw new IllegalArgumentException("page info cannot be null");
        }
        // TODO make defensive copy
        this.edges = edges;
        this.pageInfo = pageInfo;
    }

    @Override
    public List<Edge<T>> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * @deprecated prefer {@link #DefaultConnection(List, PageInfo)} and avoid mutation
     * @param edges edges
     */
    @Deprecated
    public void setEdges(List<Edge<T>> edges) {
        if (edges == null) { // TODO remove setter
            edges = Collections.emptyList();
        }
        this.edges = edges;
    }

    @Override
    public PageInfo getPageInfo() {
        return pageInfo;
    }

    /**
     * @deprecated prefer {@link #DefaultConnection(List, PageInfo)} and avoid mutation
     * @param pageInfo page info
     */
    @Deprecated
    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultConnection{");
        sb.append("edges=").append(edges);
        sb.append(", pageInfo=").append(pageInfo);
        sb.append('}');
        return sb.toString();
    }
}
