package graphql.language;

import graphql.PublicApi;

import java.util.List;
import java.util.Map;

@PublicApi
public abstract class AbstractDescribedNode<T extends Node> extends AbstractNode<T> implements DescribedNode<T>  {

    protected Description description;

    public AbstractDescribedNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData, Description description) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.description = description;
    }

    @Override
    public Description getDescription() {
        return description;
    }
}
