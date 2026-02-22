package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.language.NodeUtil.DirectivesHolder;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class OperationDefinition extends AbstractNode<OperationDefinition> implements Definition<OperationDefinition>, SelectionSetContainer<OperationDefinition>, DirectivesContainer<OperationDefinition>, NamedNode<OperationDefinition> {

    public enum Operation {
        QUERY, MUTATION, SUBSCRIPTION
    }

    private final @Nullable String name;

    private final @Nullable Operation operation;
    private final ImmutableList<VariableDefinition> variableDefinitions;
    private final DirectivesHolder directives;
    private final SelectionSet selectionSet;

    public static final String CHILD_VARIABLE_DEFINITIONS = "variableDefinitions";
    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_SELECTION_SET = "selectionSet";

    @Internal
    protected OperationDefinition(@Nullable String name,
                                  @Nullable Operation operation,
                                  List<VariableDefinition> variableDefinitions,
                                  List<Directive> directives,
                                  SelectionSet selectionSet,
                                  @Nullable SourceLocation sourceLocation,
                                  List<Comment> comments,
                                  IgnoredChars ignoredChars,
                                  Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.name = name;
        this.operation = operation;
        this.variableDefinitions = ImmutableList.copyOf(variableDefinitions);
        this.directives = DirectivesHolder.of(directives);
        this.selectionSet = selectionSet;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(variableDefinitions);
        result.addAll(directives.getDirectives());
        result.add(selectionSet);
        return result;
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_VARIABLE_DEFINITIONS, variableDefinitions)
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .child(CHILD_SELECTION_SET, selectionSet)
                .build();
    }

    @Override
    public OperationDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .variableDefinitions(newChildren.getChildren(CHILD_VARIABLE_DEFINITIONS))
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .selectionSet(newChildren.getChildOrNull(CHILD_SELECTION_SET))
        );
    }

    @Override
    public @Nullable String getName() {
        return name;
    }

    public @Nullable Operation getOperation() {
        return operation;
    }

    public List<VariableDefinition> getVariableDefinitions() {
        return variableDefinitions;
    }

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
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        OperationDefinition that = (OperationDefinition) o;

        return Objects.equals(this.name, that.name) && operation == that.operation;

    }

    @Override
    public OperationDefinition deepCopy() {
        return new OperationDefinition(name,
                operation,
                assertNotNull(deepCopy(variableDefinitions), "variableDefinitions deepCopy should not return null"),
                assertNotNull(deepCopy(directives.getDirectives()), "directives deepCopy should not return null"),
                assertNotNull(deepCopy(selectionSet), "selectionSet deepCopy should not return null"),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "OperationDefinition{" +
                "name='" + name + '\'' +
                ", operation=" + operation +
                ", variableDefinitions=" + variableDefinitions +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitOperationDefinition(this, context);
    }

    public static Builder newOperationDefinition() {
        return new Builder();
    }

    public OperationDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private Operation operation;
        private ImmutableList<VariableDefinition> variableDefinitions = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private SelectionSet selectionSet;
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(OperationDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.operation = existing.getOperation();
            this.variableDefinitions = ImmutableList.copyOf(existing.getVariableDefinitions());
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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder operation(Operation operation) {
            this.operation = operation;
            return this;
        }

        public Builder variableDefinitions(List<VariableDefinition> variableDefinitions) {
            this.variableDefinitions = ImmutableList.copyOf(variableDefinitions);
            return this;
        }

        public Builder variableDefinition(VariableDefinition variableDefinition) {
            this.variableDefinitions = ImmutableKit.addToList(variableDefinitions, variableDefinition);
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

        public OperationDefinition build() {
            return new OperationDefinition(
                    name,
                    operation,
                    variableDefinitions,
                    directives,
                    selectionSet,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}
