package graphql.language;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Assert;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static graphql.collect.ImmutableKit.map;

@PublicApi
@NullMarked
public abstract class AbstractNode<T extends Node> implements Node<T> {

    private final @Nullable SourceLocation sourceLocation;
    private final ImmutableList<Comment> comments;
    private final IgnoredChars ignoredChars;
    private final ImmutableMap<String, String> additionalData;

    public AbstractNode(@Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        this(sourceLocation, comments, ignoredChars, ImmutableKit.emptyMap());
    }

    public AbstractNode(@Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        Assert.assertNotNull(comments, "comments can't be null");
        Assert.assertNotNull(ignoredChars, "ignoredChars can't be null");
        Assert.assertNotNull(additionalData, "additionalData can't be null");

        this.sourceLocation = sourceLocation;
        this.additionalData = ImmutableMap.copyOf(additionalData);
        this.comments = ImmutableList.copyOf(comments);
        this.ignoredChars = ignoredChars;
    }

    @Override
    public @Nullable SourceLocation getSourceLocation() {
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
    protected <V extends Node> @Nullable V deepCopy(@Nullable V nullableObj) {
        if (nullableObj == null) {
            return null;
        }
        return (V) nullableObj.deepCopy();
    }

    @SuppressWarnings("unchecked")
    protected <V extends Node> @Nullable List<V> deepCopy(@Nullable List<? extends Node> list) {
        if (list == null) {
            return null;
        }
        return map(list, n -> (V) n.deepCopy());
    }
}
