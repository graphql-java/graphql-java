package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A single-line comment. These are comments that start with a {@code #} in source documents.
 */
@PublicApi
@NullMarked
public class Comment implements Serializable {
    public final String content;
    public final @Nullable SourceLocation sourceLocation;

    public Comment(String content, @Nullable SourceLocation sourceLocation) {
        this.content = content;
        this.sourceLocation = sourceLocation;
    }

    public String getContent() {
        return content;
    }

    public @Nullable SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
