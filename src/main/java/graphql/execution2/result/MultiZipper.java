package graphql.execution2.result;

import graphql.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MultiZipper {

    private final ExecutionResultNode commonRoot;
    private final List<ExecutionResultNodeZipper> zippers;


    public MultiZipper(ExecutionResultNode commonRoot, List<ExecutionResultNodeZipper> zippers) {
        this.commonRoot = Assert.assertNotNull(commonRoot);
        this.zippers = new ArrayList<>(Assert.assertNotNull(zippers));
    }

    public ExecutionResultNode toRootNode() {
        if (zippers.size() == 0) {
            return commonRoot;
        }

        List<ExecutionResultNodeZipper> curZippers = new ArrayList<>(zippers);
        while (curZippers.size() > 1) {

            List<ExecutionResultNodeZipper> deepestZippers = getDeepestZippers(curZippers);
            Map<ExecutionResultNode, List<ExecutionResultNodeZipper>> sameParent = zipperWithSameParent(deepestZippers);

            List<ExecutionResultNodeZipper> newZippers = new ArrayList<>();
            for (Map.Entry<ExecutionResultNode, List<ExecutionResultNodeZipper>> entry : sameParent.entrySet()) {
                ExecutionResultNodeZipper newZipper = moveUp(entry.getKey(), entry.getValue());
                newZippers.add(newZipper);
            }
            curZippers.removeAll(deepestZippers);
            curZippers.addAll(newZippers);
        }
        Assert.assertTrue(curZippers.size() == 1, "illegal state");
        return curZippers.get(0).toRootNode();
    }

    public ExecutionResultNode getCommonRoot() {
        return commonRoot;
    }

    public List<ExecutionResultNodeZipper> getZippers() {
        return new ArrayList<>(zippers);
    }

    public MultiZipper withZippers(List<ExecutionResultNodeZipper> zippers) {
        return new MultiZipper(commonRoot, zippers);
    }

    public MultiZipper withReplacedZipper(ExecutionResultNodeZipper oldZipper, ExecutionResultNodeZipper newZipper) {
        int index = zippers.indexOf(oldZipper);
        Assert.assertTrue(index >= 0, "oldZipper not found");
        List<ExecutionResultNodeZipper> newZippers = new ArrayList<>(zippers);
        newZippers.set(index, newZipper);
        return new MultiZipper(commonRoot, newZippers);
    }


    private List<ExecutionResultNodeZipper> getDeepestZippers(List<ExecutionResultNodeZipper> zippers) {
        Map<Integer, List<ExecutionResultNodeZipper>> grouped =
                zippers.stream().collect(Collectors.groupingBy(executionResultNodeZipper -> executionResultNodeZipper.getBreadcrumbList().size()));
        Integer maxLevel = Collections.max(grouped.keySet());
        return grouped.get(maxLevel);
    }

    private ExecutionResultNodeZipper moveUp(ExecutionResultNode parent, List<ExecutionResultNodeZipper> sameParent) {
        Map<ExecutionResultNodePosition, ExecutionResultNode> newChildren = new LinkedHashMap<>();
        List<Breadcrumb> restBreadcrumbs = Collections.emptyList();
        for (ExecutionResultNodeZipper zipper : sameParent) {
            restBreadcrumbs = zipper.getBreadcrumbList().subList(1, zipper.getBreadcrumbList().size());
            newChildren.put(zipper.getBreadcrumbList().get(0).position, zipper.getCurNode());
        }
        ExecutionResultNode newNode = parent.withNewChildren(newChildren);
        return new ExecutionResultNodeZipper(newNode, restBreadcrumbs);
    }

    private Map<ExecutionResultNode, List<ExecutionResultNodeZipper>> zipperWithSameParent(List<ExecutionResultNodeZipper> zippers) {
        return zippers.stream().collect(Collectors.groupingBy(ExecutionResultNodeZipper::getParent));
    }

}
