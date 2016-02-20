package graphql.language;


import java.util.List;

/**
 * <p>AstComparator class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class AstComparator {


    /**
     * <p>isEqual.</p>
     *
     * @param node1 a {@link graphql.language.Node} object.
     * @param node2 a {@link graphql.language.Node} object.
     * @return a boolean.
     */
    public boolean isEqual(Node node1, Node node2) {
        if (!node1.isEqualTo(node2)) return false;
        List<Node> childs1 = node1.getChildren();
        List<Node> childs2 = node2.getChildren();
        if (childs1.size() != childs2.size()) return false;
        for (int i = 0; i < childs1.size(); i++) {
            if (!isEqual(childs1.get(i), childs2.get(i))) return false;
        }
        return true;
    }
}
