package graphql.language;


import java.util.Collections;
import java.util.List;

public abstract class AbstractNode implements Node {

    private SourceLocation sourceLocation;
    private List<String> comments = Collections.emptyList();


    public void setSourceLocation(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public List<String> getComments() {
        return comments;
    }

    public void setComments(List<String> comments) {
        this.comments = comments == null ? Collections.emptyList() : comments;
    }
}
