package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class SelectionSet extends AbstractNode<SelectionSet> {

    private final ImmutableList<Selection> selections;

    public static final String CHILD_SELECTIONS = "selections";

    @Internal
    protected SelectionSet(Collection<? extends Selection> selections, @Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.selections = ImmutableList.copyOf(selections);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param selections the list of selection in this selection set
     */
    public SelectionSet(Collection<? extends Selection> selections) {
        this(selections, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public List<Selection> getSelections() {
        return selections;
    }

    /**
     * Returns a list of selections of the specific type.  It uses {@link java.lang.Class#isAssignableFrom(Class)} for the test
     *
     * @param selectionClass the selection class
     * @param <T>            the type of selection
     *
     * @return a list of selections of that class or empty list
     */
    public <T extends Selection> List<T> getSelectionsOfType(Class<T> selectionClass) {
        return ImmutableKit.filterAndMap(selections,
                d -> selectionClass.isAssignableFrom(d.getClass()),
                selectionClass::cast);
    }

    @Override
    public List<Node> getChildren() {
        return ImmutableList.copyOf(selections);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_SELECTIONS, selections)
                .build();
    }

    @Override
    public SelectionSet withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .selections(newChildren.getChildren(CHILD_SELECTIONS))
        );
    }

    @Override
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return true;
    }

    @Override
    public SelectionSet deepCopy() {
        return new SelectionSet(assertNotNull(deepCopy(selections)), getSourceLocation(), getComments(), getIgnoredChars(), getAdditionalData());
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

    public static Builder newSelectionSet(Collection<? extends Selection> selections) {
        return new Builder().selections(selections);
    }

    public SelectionSet transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeBuilder {

        private ImmutableList<Selection> selections = emptyList();
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(SelectionSet existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.selections = ImmutableList.copyOf(existing.getSelections());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder selections(Collection<? extends Selection> selections) {
            this.selections = ImmutableList.copyOf(selections);
            return this;
        }

        public Builder selection(Selection selection) {
            this.selections = ImmutableKit.addToList(selections, selection);
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }

        public SelectionSet build() {
            return new SelectionSet(selections, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
