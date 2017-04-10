package graphql.language;


import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;

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
        assertNotNull(comments, "You must provide non null comments");
        this.comments = comments;
    }
}
