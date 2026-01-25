package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class SchemaDefinition extends AbstractDescribedNode<SchemaDefinition> implements SDLDefinition<SchemaDefinition>, DirectivesContainer<SchemaDefinition> {

    private final NodeUtil.DirectivesHolder directives;
    private final ImmutableList<OperationTypeDefinition> operationTypeDefinitions;

    public static final String CHILD_DIRECTIVES = "directives";
    public static final String CHILD_OPERATION_TYPE_DEFINITIONS = "operationTypeDefinitions";


    @Internal
    protected SchemaDefinition(List<Directive> directives,
                               List<OperationTypeDefinition> operationTypeDefinitions,
                               @Nullable SourceLocation sourceLocation,
                               List<Comment> comments,
                               IgnoredChars ignoredChars,
                               Map<String, String> additionalData,
                               @Nullable Description description) {
        super(sourceLocation, comments, ignoredChars, additionalData, description);
        this.directives = NodeUtil.DirectivesHolder.of(directives);
        this.operationTypeDefinitions = ImmutableList.copyOf(operationTypeDefinitions);
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

    public List<OperationTypeDefinition> getOperationTypeDefinitions() {
        return operationTypeDefinitions;
    }

    public @Nullable Description getDescription() {
        return description;
    }

    @Override
    public List<Node> getChildren() {
        return FpKit.concat(directives.getDirectives(), operationTypeDefinitions);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .children(CHILD_OPERATION_TYPE_DEFINITIONS, operationTypeDefinitions)
                .build();
    }

    @Override
    public SchemaDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .operationTypeDefinitions(newChildren.getChildren(CHILD_OPERATION_TYPE_DEFINITIONS))
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
    public SchemaDefinition deepCopy() {
        return new SchemaDefinition(assertNotNull(deepCopy(directives.getDirectives())), assertNotNull(deepCopy(operationTypeDefinitions)), getSourceLocation(), getComments(),
                getIgnoredChars(), getAdditionalData(), description);
    }

    @Override
    public String toString() {
        return "SchemaDefinition{" +
                "directives=" + directives +
                ", operationTypeDefinitions=" + operationTypeDefinitions +
                "}";
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitSchemaDefinition(this, context);
    }

    public SchemaDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newSchemaDefinition() {
        return new Builder();
    }

    @NullUnmarked
    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<OperationTypeDefinition> operationTypeDefinitions = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();
        private Description description;


        protected Builder() {
        }

        protected Builder(SchemaDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.operationTypeDefinitions = ImmutableList.copyOf(existing.getOperationTypeDefinitions());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
            this.description = existing.getDescription();
        }

        public Builder description(Description description) {
            this.description = description;
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

        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives = ImmutableKit.addToList(directives, directive);
            return this;
        }

        public Builder operationTypeDefinitions(List<OperationTypeDefinition> operationTypeDefinitions) {
            this.operationTypeDefinitions = ImmutableList.copyOf(operationTypeDefinitions);
            return this;
        }

        public Builder operationTypeDefinition(OperationTypeDefinition operationTypeDefinition) {
            this.operationTypeDefinitions = ImmutableKit.addToList(operationTypeDefinitions, operationTypeDefinition);
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

        public SchemaDefinition build() {
            return new SchemaDefinition(directives,
                    operationTypeDefinitions,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData,
                    description);
        }
    }
}
