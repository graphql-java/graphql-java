package benchmark.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class JNodeData {

    public static JNodes.JObjectNode buildJNodeRoot() {
        AtomicLong count = new AtomicLong();
        System.out.println("\tBuilding Jnode tree...");
        List<JNodes.JNode> children = buildChildren(0, 3, 5, 20, count);
        JNodes.JObjectNode rootNode = new JNodes.JObjectNode(children);
        System.out.printf("\tBuilt tree of %d Jnodes\n", count.get());
        return rootNode;
    }

    static List<JNodes.JNode> buildChildren(int currentDepth, int maxDepth, int numObjects, int numLeafs, AtomicLong count) {

        if (currentDepth > maxDepth) {
            return Collections.emptyList();
        }
        //String spaces = currentDepth == 0 ? "" : String.format("%" + currentDepth + "c", ' ');
        //System.out.printf("%s%d\n", spaces, currentDepth);

        List<JNodes.JNode> childNodes = new ArrayList<>();
        for (int i = 0; i < numObjects; i++) {

            List<JNodes.JNode> children = buildChildren(currentDepth + 1, maxDepth, numObjects, numLeafs, count);
            childNodes.add(new JNodes.JObjectNode(children));
            count.incrementAndGet();
        }
        for (int i = 0; i < numLeafs; i++) {
            childNodes.add(new JNodes.JLeafNode("leaf-" + currentDepth + "-" + i));
            count.incrementAndGet();
        }
        return childNodes;
    }

}
