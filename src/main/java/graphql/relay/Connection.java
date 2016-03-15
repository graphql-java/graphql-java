package graphql.relay;


import java.util.ArrayList;
import java.util.List;

public class Connection {
    private List<Edge> edges = new ArrayList<Edge>();

    private PageInfo pageInfo;

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public PageInfo getPageInfo() {
        return pageInfo;
    }

    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }
}
