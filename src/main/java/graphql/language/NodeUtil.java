package graphql.language;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.execution.UnknownOperationException;
import graphql.util.FpKit;
import graphql.util.NodeLocation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static graphql.util.FpKit.mergeFirst;
import static java.util.Objects.requireNonNull;

/**
 * Helper class for working with {@link Node}s
 */
@Internal
public class NodeUtil {

    public static <T extends NamedNode<T>> T findNodeByName(List<T> namedNodes, String name) {
        for (T namedNode : namedNodes) {
            if (Objects.equals(namedNode.getName(), name)) {
                return namedNode;
            }
        }
        return null;
    }

    public static Map<String, ImmutableList<Directive>> allDirectivesByName(List<Directive> directives) {
        return FpKit.groupingBy(directives, Directive::getName);
    }

    public static <T extends NamedNode<T>> Map<String, T> nodeByName(List<T> nameNode) {
        return FpKit.getByName(nameNode, NamedNode::getName, mergeFirst());
    }


    public static class GetOperationResult {
        public OperationDefinition operationDefinition;
        public Map<String, FragmentDefinition> fragmentsByName;
    }

    public static Map<String, FragmentDefinition> getFragmentsByName(Document document) {
        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();

        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        return fragmentsByName;
    }

    public static GetOperationResult getOperation(Document document, String operationName) {


        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        Map<String, OperationDefinition> operationsByName = new LinkedHashMap<>();

        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                operationsByName.put(operationDefinition.getName(), operationDefinition);
            }
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        boolean operationNameProvided = operationName != null && !operationName.isEmpty();
        if (!operationNameProvided && operationsByName.size() > 1) {
            throw new UnknownOperationException("Must provide operation name if query contains multiple operations.");
        }
        OperationDefinition operation;

        if (!operationNameProvided) {
            operation = operationsByName.values().iterator().next();
        } else {
            operation = operationsByName.get(operationName);
        }
        if (operation == null) {
            throw new UnknownOperationException(String.format("Unknown operation named '%s'.", operationName));
        }
        GetOperationResult result = new GetOperationResult();
        result.fragmentsByName = fragmentsByName;
        result.operationDefinition = operation;
        return result;
    }

    public static void assertNewChildrenAreEmpty(NodeChildrenContainer newChildren) {
        if (!newChildren.isEmpty()) {
            throw new IllegalArgumentException("Cannot pass non-empty newChildren to Node that doesn't hold children");
        }
    }

    public static Node removeChild(Node node, NodeLocation childLocationToRemove) {
        NodeChildrenContainer namedChildren = node.getNamedChildren();
        NodeChildrenContainer newChildren = namedChildren.transform(builder -> builder.removeChild(childLocationToRemove.getName(), childLocationToRemove.getIndex()));
        return node.withNewChildren(newChildren);
    }


    /**
     * A simple directives holder that makes it easier for {@link DirectivesContainer} classes
     * to have their methods AND be efficient via immutable structures
     */
    @Internal
    static class DirectivesHolder implements Serializable {
        private final ImmutableList<Directive> directives;
        private final ImmutableMap<String, List<Directive>> directivesByName;

        static DirectivesHolder of(List<Directive> directives) {
            return new DirectivesHolder(directives);
        }

        DirectivesHolder(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            directivesByName = ImmutableMap.copyOf(allDirectivesByName(directives));
        }

        ImmutableList<Directive> getDirectives() {
            return directives;
        }

        ImmutableMap<String, List<Directive>> getDirectivesByName() {
            return directivesByName;
        }

        ImmutableList<Directive> getDirectives(String directiveName) {
            return ImmutableList.copyOf(requireNonNull(directivesByName.getOrDefault(directiveName, ImmutableList.of())));
        }

        boolean hasDirective(String directiveName) {
            return directivesByName.containsKey(directiveName);
        }

        @Override
        public String toString() {
            return directives.toString();
        }
    }
}
