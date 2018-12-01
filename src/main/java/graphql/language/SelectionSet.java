package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class SelectionSet extends AbstractNode<SelectionSet> {

    private final List<Selection> selections = new ArrayList<>();

    private static final String CHILD_SELECTIONS = "selections";

    @Internal
    protected SelectionSet(List<Selection> selections, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        super(sourceLocation, comments, ignoredChars);
        this.selections.addAll(selections);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param selections the list of selection in this selection set
     */
    public SelectionSet(List<Selection> selections) {
        this(selections, null, new ArrayList<>(), IgnoredChars.EMPTY);
    }

    public List<Selection> getSelections() {
        return selections;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(selections);
        return result;
    }

    @Override
    public ChildrenContainer getNamedChildren() {
        return ChildrenContainer.newChildrenContainer()
                .children(CHILD_SELECTIONS, selections)
                .build();
    }

    @Override
    public SelectionSet withNewChildren(ChildrenContainer newChildren) {
        return transform(builder -> builder
                .selections(newChildren.getChildren(CHILD_SELECTIONS))
        );
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SelectionSet that = (SelectionSet) o;

        return true;

    }

    @Override
    public SelectionSet deepCopy() {
        return new SelectionSet(deepCopy(selections), getSourceLocation(), getComments(), getIgnoredChars());
    }

    @Override
    public String toString() {
        return "SelectionSet{" +
                "selections=" + selections +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitSelectionSet(this, context);
    }

    public static Builder newSelectionSet() {
        return new Builder();
    }

    public static Builder newSelectionSet(List<Selection> selections) {
        return new Builder().selections(selections);
    }

    public SelectionSet transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {

        private List<Selection> selections = new ArrayList<>();
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;

        private Builder() {
        }

        private Builder(SelectionSet existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.selections = existing.getSelections();
            this.ignoredChars = existing.getIgnoredChars();
        }

        public Builder selections(List<Selection> selections) {
            this.selections = selections;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public SelectionSet build() {
            SelectionSet selectionSet = new SelectionSet(selections, sourceLocation, comments, ignoredChars);
            return selectionSet;
        }
    }
}
