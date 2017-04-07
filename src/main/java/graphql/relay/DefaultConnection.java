package graphql.relay;

import java.util.ArrayList;
import java.util.List;

public class DefaultConnection implements Connection {

    private List<Edge> edges = new ArrayList<>();

    private PageInfo pageInfo;

    @Override
    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    @Override
    public PageInfo getPageInfo() {
        return pageInfo;
    }

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
