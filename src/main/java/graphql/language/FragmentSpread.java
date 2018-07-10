package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class FragmentSpread extends AbstractNode<FragmentSpread> implements Selection<FragmentSpread>, DirectivesContainer<FragmentSpread> {

    private final String name;
    private final List<Directive> directives;

    private FragmentSpread(String name, List<Directive> directives, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.directives = new ArrayList<>(directives);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FragmentSpread that = (FragmentSpread) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }


    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        return result;
    }

    @Override
    public FragmentSpread deepCopy() {
        return new FragmentSpread(name, deepCopy(directives), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "FragmentSpread{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitFragmentSpread(this, context);
    }

    public static Builder newFragmentSpread() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private List<Directive> directives = new ArrayList<>();

        private Builder() {
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public FragmentSpread build() {
            FragmentSpread fragmentSpread = new FragmentSpread(name, directives, sourceLocation, comments);
            return fragmentSpread;
        }
    }
}
