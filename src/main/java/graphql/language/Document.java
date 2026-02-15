package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
@NullMarked
public class Document extends AbstractNode<Document> {

    private final ImmutableList<Definition> definitions;

    public static final String CHILD_DEFINITIONS = "definitions";

    @Internal
    protected Document(List<Definition> definitions, @Nullable SourceLocation sourceLocation, List<Comment> comments,
            IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.definitions = ImmutableList.copyOf(definitions);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param definitions the definitions that make up this document
     */
    public Document(List<Definition> definitions) {
        this(definitions, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public List<Definition> getDefinitions() {
        return definitions;
    }

    /**
     * Returns a list of definitions of the specific type. It uses
     * {@link java.lang.Class#isAssignableFrom(Class)} for the test
     *
     * @param definitionClass the definition class
     * @param <T>             the type of definition
     *
     * @return a list of definitions of that class or empty list
     */
    public <T extends Definition> List<T> getDefinitionsOfType(Class<T> definitionClass) {
        return ImmutableKit.filterAndMap(definitions,
                d -> definitionClass.isAssignableFrom(d.getClass()),
                definitionClass::cast);
    }

    /**
     * Returns the first of the specific type. It uses
     * {@link java.lang.Class#isAssignableFrom(Class)} for the test.
     *
     * This is useful when you have generated a document in code and KNOW there is
     * only one definition in it
     *
     * @param definitionClass the definition class
     * @param <T>             the type of definition
     *
     * @return an optional definition which will be empty of there are none
     */
    public <T extends Definition> Optional<T> getFirstDefinitionOfType(Class<T> definitionClass) {
        return definitions.stream()
                .filter(d -> definitionClass.isAssignableFrom(d.getClass()))
                .map(definitionClass::cast)
                .findFirst();
    }

    /**
     * This will allow you to find a {@link OperationDefinition} with the specified
     * name
     * in the document
     *
     * @param name the name of the operation to find
     *
     * @return an optional {@link OperationDefinition}
     */
    public Optional<OperationDefinition> getOperationDefinition(String name) {
        Assert.assertNotNull(name);
        return definitions.stream()
                .filter(d -> OperationDefinition.class.isAssignableFrom(d.getClass()))
                .map(OperationDefinition.class::cast)
                .filter(opDef -> name.equals(opDef.getName()))
                .findFirst();
    }

    @Override
    public List<Node> getChildren() {
        return ImmutableList.copyOf(definitions);
    }

    @Override
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_DEFINITIONS, definitions)
                .build();
    }

    @Override
    public Document withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .definitions(newChildren.getChildren(CHILD_DEFINITIONS)));
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
    public Document deepCopy() {
        return new Document(assertNotNull(deepCopy(definitions)), getSourceLocation(), getComments(), getIgnoredChars(),
                getAdditionalData());
    }

    @Override
    public String toString() {
        return "Document{" +
                "definitions=" + definitions +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitDocument(this, context);
    }

    public static Builder newDocument() {
        return new Builder();
    }

    public Document transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeBuilder {
        private ImmutableList<Definition> definitions = emptyList();
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(Document existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.definitions = ImmutableList.copyOf(existing.getDefinitions());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }

        public Builder definitions(List<Definition> definitions) {
            this.definitions = ImmutableList.copyOf(definitions);
            return this;
        }

        public Builder definition(Definition definition) {
            this.definitions = ImmutableKit.addToList(definitions, definition);
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

        public Document build() {
            return new Document(definitions, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
