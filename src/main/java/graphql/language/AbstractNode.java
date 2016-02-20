package graphql.language;


/**
 * <p>Abstract AbstractNode class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public abstract class AbstractNode implements Node {

    private SourceLocation sourceLocation;


    /**
     * <p>Setter for the field <code>sourceLocation</code>.</p>
     *
     * @param sourceLocation a {@link graphql.language.SourceLocation} object.
     */
    public void setSourceLocation(SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    /** {@inheritDoc} */
    @Override
    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
