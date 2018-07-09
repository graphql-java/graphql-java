package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TypeName extends AbstractNode<TypeName> implements Type<TypeName> {

    private String name;

    public TypeName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeName that = (TypeName) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public TypeName deepCopy() {
        return new TypeName(name);
    }

    @Override
    public String toString() {
        return "TypeName{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitTypeName(this, context);
    }

    public static Builder newTypeName() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private String name;
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
        }


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public TypeName build() {
            TypeName typeName = new TypeName(name);
            typeName.setSourceLocation(sourceLocation);
            typeName.setComments(comments);
            return typeName;
        }
    }
}
