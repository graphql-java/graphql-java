package graphql.language;


import graphql.Assert;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@PublicApi
public abstract class AbstractNode<T extends Node> implements Node<T> {

    private final SourceLocation sourceLocation;
    private final List<Comment> comments;
    private final IgnoredChars ignoredChars;

    public AbstractNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        this.sourceLocation = sourceLocation;
        Assert.assertNotNull(comments, "comments can't be null");
        this.comments = new ArrayList<>(comments);
        this.ignoredChars = Assert.assertNotNull(ignoredChars, "ignoredChars can't be null");
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public List<Comment> getComments() {
        return new ArrayList<>(comments);
    }

    @Override
    public IgnoredChars getIgnoredChars() {
        return ignoredChars;
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
