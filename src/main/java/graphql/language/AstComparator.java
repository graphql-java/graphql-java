package graphql.language;


import java.util.List;
import java.util.Iterator;

public class AstComparator {


    public boolean isEqual(Node node1, Node node2) {
        if (null == node1) return null == node2;
        if (!node1.isEqualTo(node2)) return false;
        List<Node> childs1 = node1.getChildren();
        List<Node> childs2 = node2.getChildren();
        if (childs1.size() != childs2.size()) return false;
        for (int i = 0; i < childs1.size(); i++) {
            if (!isEqual(childs1.get(i), childs2.get(i))) return false;
        }
        return true;
    }

    public boolean isEqual(List<Node> nodes1, List<Node> nodes2) {
        if ( nodes1.size() != nodes2.size() ) return false;
        Iterator<Node> iter1 = nodes1.iterator();
        Iterator<Node> iter2 = nodes2.iterator();
        while (iter1.hasNext()) {
            if (!isEqual(iter1.next(), iter2.next())) return false;
        }
        return true;
    }
}
