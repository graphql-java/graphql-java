package graphql.relay;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

@PublicApi
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
     * @param edges    edges
     * @param pageInfo page info
     *
     * @throws IllegalArgumentException if edges or page info is null. use {@link Collections#emptyList()} for empty edges.
     */
    public DefaultConnection(List<Edge<T>> edges, PageInfo pageInfo) {
        // TODO make defensive copy
        this.edges = assertNotNull(edges, "edges cannot be null");
        this.pageInfo = assertNotNull(pageInfo, "page info cannot be null");
    }

    @Override
    public List<Edge<T>> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * @param edges edges
     *
     * @deprecated prefer {@link #DefaultConnection(List, PageInfo)} and avoid mutation
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
     * @param pageInfo page info
     *
     * @deprecated prefer {@link #DefaultConnection(List, PageInfo)} and avoid mutation
     */
    @Deprecated
    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }

    @Override
    public String toString() {
        return "DefaultConnection{" +
                "edges=" + edges +
                ", pageInfo=" + pageInfo +
                '}';
    }
}
