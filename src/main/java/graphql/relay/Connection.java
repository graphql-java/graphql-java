package graphql.relay;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>Connection class.</p>
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class Connection {
    private List<Edge> edges =  new ArrayList<>();

    private PageInfo pageInfo;

    /**
     * <p>Getter for the field <code>edges</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Edge> getEdges() {
        return edges;
    }

    /**
     * <p>Setter for the field <code>edges</code>.</p>
     *
     * @param edges a {@link java.util.List} object.
     */
    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    /**
     * <p>Getter for the field <code>pageInfo</code>.</p>
     *
     * @return a {@link graphql.relay.PageInfo} object.
     */
    public PageInfo getPageInfo() {
        return pageInfo;
    }

    /**
     * <p>Setter for the field <code>pageInfo</code>.</p>
     *
     * @param pageInfo a {@link graphql.relay.PageInfo} object.
     */
    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }
}
