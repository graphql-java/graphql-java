package graphql.language;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

@PublicApi
@NullMarked
public class Description implements Serializable {
    public final String content;
    public final @Nullable SourceLocation sourceLocation;
    public final boolean multiLine;

    public Description(String content, @Nullable SourceLocation sourceLocation, boolean multiLine) {
        this.content = content;
        this.sourceLocation = sourceLocation;
        this.multiLine = multiLine;
    }

    public String getContent() {
        return content;
    }

    public @Nullable SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public boolean isMultiLine() {
        return multiLine;
    }
}
