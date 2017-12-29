package graphql.language;


import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;

public abstract class AbstractNode<T extends Node> implements Node<T> {

    private SourceLocation sourceLocation;
    private List<Comment> comments = Collections.emptyList();


    public void setSourceLocation(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        assertNotNull(comments, "You must provide non null comments");
        this.comments = comments;
    }

    @SuppressWarnings("unchecked")
    protected <V extends Node> V deepCopy(V nullableObj) {
        if (nullableObj == null) {
            return null;
        }
        return (V) nullableObj.deepCopy();
    }

    @SuppressWarnings("unchecked")
    protected <V extends Node> List<V> deepCopy(List<? extends Node> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(Node::deepCopy).map(node -> (V) node).collect(Collectors.toList());
    }

}
