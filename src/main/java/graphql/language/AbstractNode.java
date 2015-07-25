package graphql.language;


public abstract class AbstractNode implements Node {

    private SourceLocation sourceLocation;


    public void setSourceLocation(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
