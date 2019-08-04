package benchmark.nodes;

import benchmark.nodes.VNodes.VLeafNode;
import benchmark.nodes.VNodes.VNode;
import benchmark.nodes.VNodes.VObjectNode;
import io.vavr.collection.List;

import java.util.concurrent.atomic.AtomicLong;

public class VNodeData {

    public static VObjectNode buildVNodeRoot() {
        AtomicLong count = new AtomicLong();
        System.out.println("\tBuilding Vnode tree...");
        List<VNode> children = buildChildren(0, 3, 5, 20, count);
        VObjectNode rootNode = new VObjectNode(children);
        System.out.printf("\tBuilt tree of %d Vnodes\n", count.get());
        return rootNode;
    }

    static List<VNode> buildChildren(int currentDepth, int maxDepth, int numObjects, int numLeafs, AtomicLong count) {

        if (currentDepth > maxDepth) {
            return List.empty();
        }
        //String spaces = currentDepth == 0 ? "" : String.format("%" + currentDepth + "c", ' ');
        //System.out.printf("%s%d\n", spaces, currentDepth);

        List<VNode> childNodes = List.empty();
        for (int i = 0; i < numObjects; i++) {

            List<VNode> children = buildChildren(currentDepth + 1, maxDepth, numObjects, numLeafs, count);
            childNodes = childNodes.append(new VObjectNode(children));
            count.incrementAndGet();
        }
        for (int i = 0; i < numLeafs; i++) {
            childNodes = childNodes.append(new VLeafNode("leaf-" + currentDepth + "-" + i));
            count.incrementAndGet();
        }
        return childNodes;
    }

}
