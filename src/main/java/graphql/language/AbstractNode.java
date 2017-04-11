package graphql.language;


import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

public abstract class AbstractNode implements Node {

    private SourceLocation sourceLocation;
    private List<Comment> comments = Collections.emptyList();


    public void setSourceLocation(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        assertNotNull(comments, "You must provide non null comments");
        this.comments = comments;
    }
}
