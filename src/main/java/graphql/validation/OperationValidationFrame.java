package graphql.validation;

import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.language.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static graphql.Assert.assertNotNull;

@Internal
@NullMarked
public final class OperationValidationFrame {
    private final Node<?> node;
    private final boolean fragmentExpansion;
    private final long fieldOccurrencesBefore;
    private final int incompleteOccurrencesBefore;
    private boolean entered;
    private boolean childrenStarted;
    private @Nullable List<Node> children;
    private int nextChild;

    public OperationValidationFrame(Node<?> node) {
        this(node, false, 0, 0);
    }

    public OperationValidationFrame(Node<?> node, List<Node> children) {
        this(node);
        this.entered = true;
        this.children = children;
    }

    public OperationValidationFrame(FragmentDefinition fragment, long fieldOccurrencesBefore, int incompleteOccurrencesBefore) {
        this(fragment, true, fieldOccurrencesBefore, incompleteOccurrencesBefore);
    }

    private OperationValidationFrame(Node<?> node, boolean fragmentExpansion, long fieldOccurrencesBefore, int incompleteOccurrencesBefore) {
        this.node = node;
        this.fragmentExpansion = fragmentExpansion;
        this.fieldOccurrencesBefore = fieldOccurrencesBefore;
        this.incompleteOccurrencesBefore = incompleteOccurrencesBefore;
    }

    public Node<?> getNode() {
        return node;
    }

    public boolean enter() {
        if (entered) {
            return false;
        }
        entered = true;
        return true;
    }

    public boolean startChildren() {
        if (childrenStarted) {
            return false;
        }
        childrenStarted = true;
        if (children == null) {
            children = node.getChildren();
        }
        return true;
    }

    public @Nullable Node<?> nextChild() {
        List<Node> nodes = assertNotNull(children);
        while (nextChild < nodes.size()) {
            Node<?> child = nodes.get(nextChild++);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    public boolean isFragmentExpansion() {
        return fragmentExpansion;
    }

    public long getFieldOccurrencesBefore() {
        return fieldOccurrencesBefore;
    }

    public int getIncompleteOccurrencesBefore() {
        return incompleteOccurrencesBefore;
    }
}
