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

import java.util.ArrayList;
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
public class InlineFragment extends AbstractNode<InlineFragment> implements Selection<InlineFragment>, SelectionSetContainer<InlineFragment>, DirectivesContainer<InlineFragment> {
    private final @Nullable TypeName typeCondition;
    private final NodeUtil.DirectivesHolder directives;
    private final SelectionSet selectionSet;

    public static final String CHILD_TYPE_CONDITION = "typeCondition";
    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_SELECTION_SET = "selectionSet";

    @Internal
    protected InlineFragment(@Nullable TypeName typeCondition,
                             List<Directive> directives,
                             SelectionSet selectionSet,
                             @Nullable SourceLocation sourceLocation,
                             List<Comment> comments,
                             IgnoredChars ignoredChars,
                             Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.typeCondition = typeCondition;
        this.directives = NodeUtil.DirectivesHolder.of(directives);
        this.selectionSet = selectionSet;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param typeCondition the type condition of the inline fragment
     */
    public InlineFragment(@Nullable TypeName typeCondition) {
        this(typeCondition, emptyList(), SelectionSet.newSelectionSet().build(), null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param typeCondition the type condition of the inline fragment
     * @param selectionSet  of the inline fragment
     */
    public InlineFragment(@Nullable TypeName typeCondition, SelectionSet selectionSet) {
        this(typeCondition, emptyList(), selectionSet, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public @Nullable TypeName getTypeCondition() {
        return typeCondition;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives.getDirectives();
    }

    @Override
    public Map<String, List<Directive>> getDirectivesByName() {
        return directives.getDirectivesByName();
    }

    @Override
    public List<Directive> getDirectives(String directiveName) {
        return directives.getDirectives(directiveName);
    }

    @Override
    public boolean hasDirective(String directiveName) {
        return directives.hasDirective(directiveName);
    }

    @Override
    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        if (typeCondition != null) {
            result.add(typeCondition);
        }
        result.addAll(directives.getDirectives());
        result.add(selectionSet);
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .child(CHILD_TYPE_CONDITION, typeCondition)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .child(CHILD_SELECTION_SET, selectionSet)
                .build();
    }

    @Override
    public InlineFragment withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .typeCondition(newChildren.getChildOrNull(CHILD_TYPE_CONDITION))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .selectionSet(newChildren.getChildOrNull(CHILD_SELECTION_SET))
        );
    }

    @Override
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public InlineFragment deepCopy() {
        return new InlineFragment(
                deepCopy(typeCondition),
                assertNotNull(deepCopy(directives.getDirectives()), "directives cannot be null"),
                assertNotNull(deepCopy(selectionSet), "selectionSet cannot be null"),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "InlineFragment{" +
                "typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInlineFragment(this, context);
    }

    public static Builder newInlineFragment() {
        return new Builder();
    }

    public InlineFragment transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private TypeName typeCondition;
        private ImmutableList<Directive> directives = emptyList();
        private SelectionSet selectionSet;
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }


        private Builder(InlineFragment existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.typeCondition = existing.getTypeCondition();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.selectionSet = existing.getSelectionSet();
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder typeCondition(TypeName typeCondition) {
            this.typeCondition = typeCondition;
            return this;
        }

        @Override
        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives = ImmutableKit.addToList(directives, directive);
            return this;
        }


        public Builder selectionSet(SelectionSet selectionSet) {
            this.selectionSet = selectionSet;
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


        public InlineFragment build() {
            return new InlineFragment(typeCondition, directives, selectionSet, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
