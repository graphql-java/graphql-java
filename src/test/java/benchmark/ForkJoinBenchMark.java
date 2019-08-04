package benchmark;

import graphql.Scalars;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.FetchedValue;
import graphql.execution.nextgen.FetchedValueAnalysis;
import graphql.execution.nextgen.result.ExecutionResultNode;
import graphql.execution.nextgen.result.LeafExecutionResultNode;
import graphql.execution.nextgen.result.ObjectExecutionResultNode;
import graphql.execution.nextgen.result.RootExecutionResultNode;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TraverserVisitorStub;
import graphql.util.TreeTransformer;
import graphql.util.TreeTransformerUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import static graphql.execution.FetchedValue.newFetchedValue;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR;
import static graphql.execution.nextgen.FetchedValueAnalysis.newFetchedValueAnalysis;
import static graphql.execution.nextgen.result.ResultNodeAdapter.RESULT_NODE_ADAPTER;

/**
 * See http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 *
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 *
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class ForkJoinBenchMark {

    static ExecutionResultNode rootNode = buildResultNodeTree();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkResultTransformThroughput() {
        transformEveryLeafToUpper();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkResultTransformAvgTime() {
        transformEveryLeafToUpper();
    }


    public static ExecutionResultNode transformEveryLeafToUpper() {
        TraverserVisitor<ExecutionResultNode> visitor = new TraverserVisitorStub<ExecutionResultNode>() {
            @Override
            public TraversalControl enter(TraverserContext<ExecutionResultNode> context) {
                if (context.thisNode() instanceof LeafExecutionResultNode) {
                    LeafExecutionResultNode leaf = (LeafExecutionResultNode) context.thisNode();
                    String upper = ((String) leaf.getValue()).toUpperCase();
                    LeafExecutionResultNode newLeaf = new LeafExecutionResultNode(fvaForValue(upper), null);
                    return TreeTransformerUtil.changeNode(context, newLeaf);
                }
                return TraversalControl.CONTINUE;
            }
        };
        TreeTransformer<ExecutionResultNode> treeTransformer = new TreeTransformer<>(RESULT_NODE_ADAPTER);
        return treeTransformer.transform(rootNode, visitor, Collections.emptyMap());
    }

    public static void mainX(String[] args) {
        long then = System.currentTimeMillis();
        transformEveryLeafToUpper();
        long now = System.currentTimeMillis() - then;
        System.out.printf("Took : %d\n", Duration.ofMillis(now).toMillis());
    }

    private static ExecutionResultNode buildResultNodeTree() {
        AtomicLong count = new AtomicLong();
        System.out.println("\tBuilding tree...");
        List<ExecutionResultNode> children = buildChildren(0, 3, 5, 20, count);
        RootExecutionResultNode rootExecutionResultNode = new RootExecutionResultNode(children);
        System.out.printf("\tBuilt tree of %d nodes\n", count.get());
        return rootExecutionResultNode;
    }

    private static List<ExecutionResultNode> buildChildren(int currentDepth, int maxDepth, int numObjects, int numLeafs, AtomicLong count) {

        if (currentDepth > maxDepth) {
            return Collections.emptyList();
        }
        //String spaces = currentDepth == 0 ? "" : String.format("%" + currentDepth + "c", ' ');
        //System.out.printf("%s%d\n", spaces, currentDepth);

        List<ExecutionResultNode> childNodes = new ArrayList<>();
        for (int i = 0; i < numObjects; i++) {

            List<ExecutionResultNode> children = buildChildren(currentDepth + 1, maxDepth, numObjects, numLeafs, count);
            FetchedValueAnalysis fva = fvaForValue("object-" + currentDepth + "-" + i);
            childNodes.add(new ObjectExecutionResultNode(fva, children));
            count.incrementAndGet();
        }
        for (int i = 0; i < numLeafs; i++) {
            FetchedValueAnalysis fva = fvaForValue("leaf-" + currentDepth + "-" + i);
            childNodes.add(new LeafExecutionResultNode(fva, null));
            count.incrementAndGet();
        }
        return childNodes;
    }

    static FetchedValueAnalysis fvaForValue(Object value) {
        ExecutionStepInfo executionStepInfo = newExecutionStepInfo().type(Scalars.GraphQLString).build();
        FetchedValue fetchedValue = newFetchedValue()
                .fetchedValue(value)
                .rawFetchedValue(value).build();
        return newFetchedValueAnalysis()
                .executionStepInfo(executionStepInfo)
                .valueType(SCALAR)
                .fetchedValue(fetchedValue)
                .completedValue(value)
                .build();
    }


}
