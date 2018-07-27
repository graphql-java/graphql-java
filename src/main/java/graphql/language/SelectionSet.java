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

    @Internal
    protected SelectionSet(List<Selection> selections, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.selections.addAll(selections);
    }

    /**
     * alternative to using a Builder for convenience
     */
    public SelectionSet(List<Selection> selections) {
        this(selections, null, new ArrayList<>());
    }

    public List<Selection> getSelections() {
        return new ArrayList<>(selections);
    }


    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(selections);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectionSet that = (SelectionSet) o;

        return true;

    }

    @Override
    public SelectionSet deepCopy() {
        return new SelectionSet(deepCopy(selections), getSourceLocation(), getComments());
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

        private Builder() {
        }

        private Builder(SelectionSet existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.selections = existing.getSelections();
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

        public SelectionSet build() {
            SelectionSet selectionSet = new SelectionSet(selections, sourceLocation, comments);
            return selectionSet;
        }
    }
}
