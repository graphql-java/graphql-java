package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class Document extends AbstractNode<Document> {

    private List<Definition> definitions;

    public Document() {
        this(new ArrayList<>());
    }

    public Document(List<Definition> definitions) {
        this.definitions = definitions;
    }

    public List<Definition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<Definition> definitions) {
        this.definitions = definitions;
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
        return new Document(deepCopy(definitions));
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
}
