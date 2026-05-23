package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@PublicApi
@NullMarked
public abstract class AbstractDescribedNode<T extends Node> extends AbstractNode<T> implements DescribedNode<T>  {

    protected @Nullable Description description;

    public AbstractDescribedNode(@Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData, @Nullable Description description) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.description = description;
    }

    @Override
    public @Nullable Description getDescription() {
        return description;
    }
}
