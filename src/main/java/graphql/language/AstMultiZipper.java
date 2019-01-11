package graphql.language;

import graphql.PublicApi;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertTrue;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * A collection of {@link AstZipper} sharing all the same root node.
 * It is used to track multiple changes at once, while {@link AstZipper} focus on one Node.
 */
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
                Optional<AstZipper> zipperToBeReplaced = curZippers.stream().filter(zipper -> zipper.getCurNode() == entry.getKey()).findFirst();
                zipperToBeReplaced.ifPresent(curZippers::remove);
                newZippers.add(newZipper);
            }
            curZippers.removeAll(deepestZippers);
            curZippers.addAll(newZippers);
        }
        assertTrue(curZippers.size() == 1, "unexpected state: all zippers must share the same root node");
        return curZippers.get(0).toRoot();
    }

    public Node getCommonRoot() {
        return commonRoot;
    }

    public List<AstZipper> getZippers() {
        return new ArrayList<>(zippers);
    }

    public AstZipper getZipperForNode(Node node) {
        return FpKit.findOne(zippers, zipper -> zipper.getCurNode() == node);
    }

    public AstMultiZipper withReplacedZippers(List<AstZipper> zippers) {
        return new AstMultiZipper(commonRoot, zippers);
    }

    public AstMultiZipper withNewZipper(AstZipper newZipper) {
        List<AstZipper> newZippers = getZippers();
        newZippers.add(newZipper);
        return new AstMultiZipper(commonRoot, newZippers);
    }

    public AstMultiZipper withReplacedZipper(AstZipper oldZipper, AstZipper newZipper) {
        int index = zippers.indexOf(oldZipper);
        assertTrue(index >= 0, "oldZipper not found");
        List<AstZipper> newZippers = new ArrayList<>(zippers);
        newZippers.set(index, newZipper);
        return new AstMultiZipper(commonRoot, newZippers);
    }


    private List<AstZipper> getDeepestZippers(List<AstZipper> zippers) {
        Map<Integer, List<AstZipper>> grouped = zippers
                .stream()
                .collect(groupingBy(astZipper -> astZipper.getBreadcrumbs().size(), LinkedHashMap::new, mapping(Function.identity(), toList())));

        Integer maxLevel = Collections.max(grouped.keySet());
        return grouped.get(maxLevel);
    }

    private AstZipper moveUp(Node parent, List<AstZipper> sameParent) {
        assertNotEmpty(sameParent, "expected at least one zipper");
        Map<String, List<Node>> childrenMap = parent.getNamedChildren().getChildren();

        for (AstZipper zipper : sameParent) {
            NodeLocation location = zipper.getBreadcrumbs().get(0).getLocation();
            childrenMap.computeIfAbsent(location.getName(), (key) -> new ArrayList<>());
            List<Node> childrenList = childrenMap.get(location.getName());
            if (childrenList.size() > location.getIndex()) {
                childrenList.set(location.getIndex(), zipper.getCurNode());
            } else {
                childrenList.add(zipper.getCurNode());
            }
        }
        Node newNode = parent.withNewChildren(newNodeChildrenContainer(childrenMap).build());
        List<AstBreadcrumb> newBreadcrumbs = sameParent.get(0).getBreadcrumbs().subList(1, sameParent.get(0).getBreadcrumbs().size());
        return new AstZipper(newNode, newBreadcrumbs);
    }

    private Map<Node, List<AstZipper>> zipperWithSameParent(List<AstZipper> zippers) {
        return zippers.stream().collect(groupingBy(AstZipper::getParent, LinkedHashMap::new,
                mapping(Function.identity(), Collectors.toList())));
    }

    @Override
    public String toString() {
        return "AstMultiZipper{" +
                "commonRoot=" + commonRoot.getClass() +
                ", zippersCount=" + zippers.size() +
                '}';
    }
}
