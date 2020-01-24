package graphql.language;

import java.util.List;
import java.util.Map;

public abstract class AbstractDescribedNode<T extends Node> extends AbstractNode<T> implements DescribedNode<T>  {

    private Description description;

    public AbstractDescribedNode(SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData, Description description) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.description = description;
    }

    @Override
    public Description getDescription() {
        return description;
    }
}
