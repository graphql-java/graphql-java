package benchmark;

import benchmark.JNodes.JNode;
import benchmark.VNodes.VLeafNode;
import benchmark.VNodes.VNode;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TraverserVisitorStub;
import graphql.util.TreeTransformer;
import graphql.util.TreeTransformerUtil;
import io.vavr.collection.HashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static benchmark.JNodeData.buildJNodeRoot;
import static benchmark.JNodes.JLeafNode;
import static benchmark.JNodes.JNodeAdapter;
import static benchmark.VNodeData.buildVNodeRoot;

/**
 * See http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 *
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 *
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 2, batchSize = 3)
@Measurement(iterations = 3, time = 5, batchSize = 3)
public class PersistentDataStructuresBenchMark {

    static JNode rootJNode = buildJNodeRoot();
    static VNode rootVNode = buildVNodeRoot();

//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void benchMarkResultTransformThroughput() {
//        jNodeTransformEveryLeafToUpper();
//    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkJNodeAvgTime() {
        jNodeTransformEveryLeafToUpper();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkVNodeAvgTime() {
        vNodeTransformEveryLeafToUpper();
    }


    public static JNode jNodeTransformEveryLeafToUpper() {
        TraverserVisitor<JNode> visitor = new TraverserVisitorStub<JNode>() {
            @Override
            public TraversalControl enter(TraverserContext<JNode> context) {
                if (context.thisNode() instanceof JLeafNode) {
                    JLeafNode leaf = (JLeafNode) context.thisNode();
                    String upper = ((String) leaf.getValue()).toUpperCase();
                    JLeafNode newLeaf = new JLeafNode(upper);
                    return TreeTransformerUtil.changeNode(context, newLeaf);
                }
                return TraversalControl.CONTINUE;
            }
        };
        TreeTransformer<JNode> treeTransformer = new TreeTransformer<>(JNodeAdapter.JNODE_ADAPTER);
        return treeTransformer.transform(rootJNode, visitor, Collections.emptyMap());
    }

    public static VNode vNodeTransformEveryLeafToUpper() {
        TraverserVisitor<VNode> visitor = new TraverserVisitorStub<VNode>() {
            @Override
            public TraversalControl enter(TraverserContext<VNode> context) {
                if (context.thisNode() instanceof VLeafNode) {
                    VLeafNode leaf = (VLeafNode) context.thisNode();
                    String upper = ((String) leaf.getValue()).toUpperCase();
                    VLeafNode newLeaf = new VLeafNode(upper);
                    return benchmark.vavr.TreeTransformerUtil.changeNode(context, newLeaf);
                }
                return TraversalControl.CONTINUE;
            }
        };
        benchmark.vavr.TreeTransformer<VNode> treeTransformer = new benchmark.vavr.TreeTransformer<>(VNodes.VNodeAdapter.VNODE_ADAPTER);
        return treeTransformer.transform(rootVNode, visitor, HashMap.empty());
    }

    public static void mainXXX(String[] args) {
        long now;
        long then;


        then = System.currentTimeMillis();
        VNode vNode = vNodeTransformEveryLeafToUpper();
        now = System.currentTimeMillis() - then;
        System.out.printf("VNode Took : %d\n", Duration.ofMillis(now).toMillis());

        then = System.currentTimeMillis();
        JNode jNode = jNodeTransformEveryLeafToUpper();
        now = System.currentTimeMillis() - then;
        System.out.printf("JNode Took : %d\n", Duration.ofMillis(now).toMillis());

    }


}
