package graphql.language;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Assert;
import graphql.PublicApi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.map;

@PublicApi
public abstract class AbstractNode<T extends Node> implements Node<T> {

    private final SourceLocation sourceLocation;
    private final ImmutableList<Comment> comments;
    private final IgnoredChars ignoredChars;
    private final ImmutableMap<String, String> additionalData;

    public AbstractNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        this(sourceLocation, comments, ignoredChars, Collections.emptyMap());
    }

    public AbstractNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        Assert.assertNotNull(comments, () -> "comments can't be null");
        Assert.assertNotNull(ignoredChars, () -> "ignoredChars can't be null");
        Assert.assertNotNull(additionalData, () -> "additionalData can't be null");

        this.sourceLocation = sourceLocation;
        this.additionalData = ImmutableMap.copyOf(additionalData);
        this.comments = ImmutableList.copyOf(comments);
        this.ignoredChars = ignoredChars;
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public List<Comment> getComments() {
        return comments;
    }

    @Override
    public IgnoredChars getIgnoredChars() {
        return ignoredChars;
    }


    public Map<String, String> getAdditionalData() {
        return additionalData;
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
        return map(list, n -> (V) n.deepCopy());
    }
}
