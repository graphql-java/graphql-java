package graphql.language;

public class Comment {
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
