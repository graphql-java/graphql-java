package graphql.language;

import graphql.PublicApi;

import java.io.Serializable;

/**
 * A single-line comment. These are comments that start with a {@code #} in source documents.
 */
@PublicApi
public class Comment implements Serializable {
    public final String content;
    public final SourceLocation sourceLocation;

    public Comment(String content, SourceLocation sourceLocation) {
        this.content = content;
        this.sourceLocation = sourceLocation;
    }

    public String getContent() {
        return content;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
