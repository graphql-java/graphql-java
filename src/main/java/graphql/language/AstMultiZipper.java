package graphql.language;

import graphql.Assert;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@PublicApi
public class AstMultiZipper {
    private final Node commonRoot;
    private final List<AstZipper> zippers;

    public AstMultiZipper(Node commonRoot, List<AstZipper> zippers) {
        this.commonRoot = commonRoot;
        this.zippers = new ArrayList<>(zippers);
    }

    public Node toRootNode() {
        if (zippers.size() == 0) {
            return commonRoot;
        }

        List<AstZipper> curZippers = new ArrayList<>(zippers);
        while (curZippers.size() > 1) {

            List<AstZipper> deepestZippers = getDeepestZippers(curZippers);
            Map<Node, List<AstZipper>> sameParent = zipperWithSameParent(deepestZippers);

            List<AstZipper> newZippers = new ArrayList<>();
            for (Map.Entry<Node, List<AstZipper>> entry : sameParent.entrySet()) {
                AstZipper newZipper = moveUp(entry.getKey(), entry.getValue());
                newZippers.add(newZipper);
            }
            curZippers.removeAll(deepestZippers);
            curZippers.addAll(newZippers);
        }
        Assert.assertTrue(curZippers.size() == 1, "illegal state");
        return curZippers.get(0).toRoot();
    }

    public Node getCommonRoot() {
        return commonRoot;
    }

    public List<AstZipper> getZippers() {
        return new ArrayList<>(zippers);
    }

    public AstMultiZipper withZippers(List<AstZipper> zippers) {
        return new AstMultiZipper(commonRoot, zippers);
    }

    public AstMultiZipper withReplacedZipper(AstZipper oldZipper, AstZipper newZipper) {
        int index = zippers.indexOf(oldZipper);
        Assert.assertTrue(index >= 0, "oldZipper not found");
        List<AstZipper> newZippers = new ArrayList<>(zippers);
        newZippers.set(index, newZipper);
        return new AstMultiZipper(commonRoot, newZippers);
    }


    private List<AstZipper> getDeepestZippers(List<AstZipper> zippers) {
        Map<Integer, List<AstZipper>> grouped =
                zippers.stream().collect(Collectors.groupingBy(astZipper -> astZipper.getBreadcrumbs().size()));
        Integer maxLevel = Collections.max(grouped.keySet());
        return grouped.get(maxLevel);
    }

    private AstZipper moveUp(Node parent, List<AstZipper> sameParent) {
        ChildrenContainer.Builder newChildren = ChildrenContainer.newChildrenContainer();
        List<AstBreadcrumb> restBreadcrumbs = Collections.emptyList();
        for (AstZipper zipper : sameParent) {
            // it is always the same actually
            restBreadcrumbs = zipper.getBreadcrumbs().subList(1, zipper.getBreadcrumbs().size());
            newChildren.child(zipper.getBreadcrumbs().get(0).getLocation().getName(), zipper.getCurNode());
        }
        Node newNode = parent.withNewChildren(newChildren.build());
        return new AstZipper(newNode, restBreadcrumbs);
    }

    private Map<Node, List<AstZipper>> zipperWithSameParent(List<AstZipper> zippers) {
        return zippers.stream().collect(Collectors.groupingBy(AstZipper::getParent));
    }


}
