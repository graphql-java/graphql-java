package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class EnumValue extends AbstractNode<EnumValue> implements Value<EnumValue>, NamedNode<EnumValue> {

    private final String name;

    private EnumValue(String name, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
    }


    /**
     * alternative to using a Builder for convenience
     *
     * @param name
     */
    public EnumValue(String name) {
        super(null, new ArrayList<>());
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumValue that = (EnumValue) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public EnumValue deepCopy() {
        return new EnumValue(name, getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "EnumValue{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitEnumValue(this, context);
    }

    public static Builder newEnumValue() {
        return new Builder();
    }

    public static Builder newEnumValue(String name) {
        return new Builder().name(name);
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public EnumValue build() {
            EnumValue enumValue = new EnumValue(name, sourceLocation, comments);
            return enumValue;
        }
    }
}
