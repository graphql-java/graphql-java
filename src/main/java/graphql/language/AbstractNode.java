package graphql.language;


import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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

    protected boolean isEqualTo(String thisStr, String thatStr) {
        if (null == thisStr) {
            if (null != thatStr) {
                return false;
            }
        } else if (!thisStr.equals(thatStr)) {
            return false;
        }
        return true;
    }

    protected <V> V deepCopy(V nullableObj, Function<V, V> copyFunction) {
        if (nullableObj == null) {
            return null;
        }
        return copyFunction.apply(nullableObj);
    }

    @SuppressWarnings("unchecked")
    protected <V extends Node> List<V> deepCopy(List<? extends Node> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(Node::deepCopy).map(node -> (V) node).collect(Collectors.toList());
    }

}
