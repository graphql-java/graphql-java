package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class Document extends AbstractNode<Document> {

    private final List<Definition> definitions;

    @Internal
    protected Document(List<Definition> definitions, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.definitions = definitions;
    }

    /**
     * alternative to using a Builder for convenience
     */
    public Document(List<Definition> definitions) {
        this(definitions, null, new ArrayList<>());
    }

    public List<Definition> getDefinitions() {
        return new ArrayList<>(definitions);
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(definitions);
    }


    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    @Override
    public Document deepCopy() {
        return new Document(deepCopy(definitions), getSourceLocation(), getComments());
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

    public static final class Builder implements NodeBuilder {
        private List<Definition> definitions = new ArrayList<>();
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();

        private Builder() {
        }

        private Builder(Document existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.definitions = existing.getDefinitions();
        }

        public Builder definitions(List<Definition> definitions) {
            this.definitions = definitions;
            return this;
        }

        public Builder definition(Definition definition) {
            this.definitions.add(definition);
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

        public Document build() {
            Document document = new Document(definitions, sourceLocation, comments);
            return document;
        }
    }
}
