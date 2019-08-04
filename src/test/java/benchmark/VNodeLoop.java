package benchmark;

import benchmark.nodes.VNodes;

import java.time.Instant;

import static benchmark.nodes.VNodeData.buildVNodeRoot;

public class VNodeLoop {

    public static void main(String[] args) {
        VNodes.VNode rootVNode = buildVNodeRoot();
        for (int i = 0; i < 1000; i++) {
            PersistentDataStructuresBenchMark.vNodeTransformEveryLeafToUpper(rootVNode);
            System.out.println("Loop" + Instant.now());
        }
    }
}
