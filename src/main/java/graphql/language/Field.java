package graphql.language;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.Interning;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.google.common.collect.ImmutableMap.copyOf;
import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.addToMap;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;

/*
 * This is provided to a DataFetcher, therefore it is a public API.
 * This might change in the future.
 */
@PublicApi
@NullMarked
public class Field extends AbstractNode<Field>
        implements Selection<Field>, SelectionSetContainer<Field>, DirectivesContainer<Field>, NamedNode<Field> {

    private final String name;
    private final @Nullable String alias;
    private final ImmutableList<Argument> arguments;
    private final NodeUtil.DirectivesHolder directives;
    private final @Nullable SelectionSet selectionSet;

    public static final String CHILD_ARGUMENTS = "arguments";
    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_SELECTION_SET = "selectionSet";

    @Internal
    protected Field(String name,
            @Nullable String alias,
            List<Argument> arguments,
            List<Directive> directives,
            @Nullable SelectionSet selectionSet,
            @Nullable SourceLocation sourceLocation,
            List<Comment> comments,
            IgnoredChars ignoredChars,
            Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.name = Interning.intern(name);
        this.alias = alias;
        this.arguments = ImmutableList.copyOf(arguments);
        this.directives = NodeUtil.DirectivesHolder.of(directives);
        this.selectionSet = selectionSet;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the field
     */
    public Field(String name) {
        this(name, null, emptyList(), emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name      of the field
     * @param arguments to the field
     */
    public Field(String name, List<Argument> arguments) {
        this(name, null, arguments, emptyList(), null, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name         of the field
     * @param arguments    to the field
     * @param selectionSet of the field
     */
    public Field(String name, List<Argument> arguments, SelectionSet selectionSet) {
        this(name, null, arguments, emptyList(), selectionSet, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name         of the field
     * @param selectionSet of the field
     */
    public Field(String name, SelectionSet selectionSet) {
        this(name, null, emptyList(), emptyList(), selectionSet, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(arguments);
        result.addAll(directives.getDirectives());
        if (selectionSet != null) {
            result.add(selectionSet);
        }
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return NodeChildrenContainer.newNodeChildrenContainer()
                .children(CHILD_ARGUMENTS, arguments)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .child(CHILD_SELECTION_SET, selectionSet)
                .build();
    }

    @Override
    public Field withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder.arguments(newChildren.getChildren(CHILD_ARGUMENTS))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .selectionSet(newChildren.getChildOrNull(CHILD_SELECTION_SET)));
    }

    @Override
    public String getName() {
        return name;
    }

    public @Nullable String getAlias() {
        return alias;
    }

    public String getResultKey() {
        return alias != null ? alias : name;
    }

    public List<Argument> getArguments() {
        return arguments;
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
    public @Nullable SelectionSet getSelectionSet() {
        return selectionSet;
    }

    @Override
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Field that = (Field) o;

        return Objects.equals(this.name, that.name) && Objects.equals(this.alias, that.alias);
    }

    @Override
    public Field deepCopy() {
        return new Field(name,
                alias,
                assertNotNull(deepCopy(arguments)),
                assertNotNull(deepCopy(directives.getDirectives())),
                deepCopy(selectionSet),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "Field{" +
                "name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", arguments=" + arguments +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitField(this, context);
    }

    public static Builder newField() {
        return new Builder();
    }

    public static Builder newField(String name) {
        return new Builder().name(name);
    }

    public static Builder newField(String name, SelectionSet selectionSet) {
        return new Builder().name(name).selectionSet(selectionSet);
    }

    public Field transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeDirectivesBuilder {
        private @Nullable SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private @Nullable String name;
        private @Nullable String alias;
        private ImmutableList<Argument> arguments = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private @Nullable SelectionSet selectionSet;
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private ImmutableMap<String, String> additionalData = emptyMap();

        private Builder() {
        }

        private Builder(Field existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.alias = existing.getAlias();
            this.arguments = ImmutableList.copyOf(existing.getArguments());
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.selectionSet = existing.getSelectionSet();
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = copyOf(existing.getAdditionalData());
        }

        public Builder sourceLocation(@Nullable SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder alias(@Nullable String alias) {
            this.alias = alias;
            return this;
        }

        public Builder arguments(List<Argument> arguments) {
            this.arguments = ImmutableList.copyOf(arguments);
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

        public Builder selectionSet(@Nullable SelectionSet selectionSet) {
            this.selectionSet = selectionSet;
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = ImmutableMap.copyOf(assertNotNull(additionalData));
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData = addToMap(this.additionalData, key, value);
            return this;
        }

        public Field build() {
            return new Field(assertNotNull(name), alias, arguments, directives, selectionSet, sourceLocation, comments,
                    ignoredChars, additionalData);
        }
    }
}
