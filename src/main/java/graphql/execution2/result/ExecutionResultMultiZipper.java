package graphql.execution2.result;

import graphql.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExecutionResultMultiZipper {

    private final ExecutionResultNode commonRoot;
    private final List<ExecutionResultZipper> zippers;


    public ExecutionResultMultiZipper(ExecutionResultNode commonRoot, List<ExecutionResultZipper> zippers) {
        this.commonRoot = Assert.assertNotNull(commonRoot);
        this.zippers = new ArrayList<>(Assert.assertNotNull(zippers));
    }

    public ExecutionResultNode toRootNode() {
        if (zippers.size() == 0) {
            return commonRoot;
        }

        List<ExecutionResultZipper> curZippers = new ArrayList<>(zippers);
        while (curZippers.size() > 1) {

            List<ExecutionResultZipper> deepestZippers = getDeepestZippers(curZippers);
            Map<ExecutionResultNode, List<ExecutionResultZipper>> sameParent = zipperWithSameParent(deepestZippers);

            List<ExecutionResultZipper> newZippers = new ArrayList<>();
            for (Map.Entry<ExecutionResultNode, List<ExecutionResultZipper>> entry : sameParent.entrySet()) {
                ExecutionResultZipper newZipper = moveUp(entry.getKey(), entry.getValue());
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

    public List<ExecutionResultZipper> getZippers() {
        return new ArrayList<>(zippers);
    }

    public ExecutionResultMultiZipper withZippers(List<ExecutionResultZipper> zippers) {
        return new ExecutionResultMultiZipper(commonRoot, zippers);
    }

    public ExecutionResultMultiZipper withReplacedZipper(ExecutionResultZipper oldZipper, ExecutionResultZipper newZipper) {
        int index = zippers.indexOf(oldZipper);
        Assert.assertTrue(index >= 0, "oldZipper not found");
        List<ExecutionResultZipper> newZippers = new ArrayList<>(zippers);
        newZippers.set(index, newZipper);
        return new ExecutionResultMultiZipper(commonRoot, newZippers);
    }


    private List<ExecutionResultZipper> getDeepestZippers(List<ExecutionResultZipper> zippers) {
        Map<Integer, List<ExecutionResultZipper>> grouped =
                zippers.stream().collect(Collectors.groupingBy(executionResultZipper -> executionResultZipper.getBreadcrumbList().size()));
        Integer maxLevel = Collections.max(grouped.keySet());
        return grouped.get(maxLevel);
    }

    private ExecutionResultZipper moveUp(ExecutionResultNode parent, List<ExecutionResultZipper> sameParent) {
        Map<ExecutionResultNodePosition, ExecutionResultNode> newChildren = new LinkedHashMap<>();
        List<Breadcrumb> restBreadcrumbs = Collections.emptyList();
        for (ExecutionResultZipper zipper : sameParent) {
            restBreadcrumbs = zipper.getBreadcrumbList().subList(1, zipper.getBreadcrumbList().size());
            newChildren.put(zipper.getBreadcrumbList().get(0).position, zipper.getCurNode());
        }
        ExecutionResultNode newNode = parent.withNewChildren(newChildren);
        return new ExecutionResultZipper(newNode, restBreadcrumbs);
    }

    private Map<ExecutionResultNode, List<ExecutionResultZipper>> zipperWithSameParent(List<ExecutionResultZipper> zippers) {
        return zippers.stream().collect(Collectors.groupingBy(ExecutionResultZipper::getParent));
    }

}
