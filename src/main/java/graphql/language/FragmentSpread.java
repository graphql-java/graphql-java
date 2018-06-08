package graphql.language;


import java.util.ArrayList;
import java.util.List;

import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

public class FragmentSpread extends AbstractNode<FragmentSpread> implements Selection<FragmentSpread>, DirectivesContainer<FragmentSpread> {

    private String name;
    private List<Directive> directives = new ArrayList<>();

    public FragmentSpread() {
    }

    public FragmentSpread(String name) {
        this.name = name;
    }

    public FragmentSpread(String name, List<Directive> directives) {
        this.name = name;
        this.directives = directives;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
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
        return new ArrayList<>(directives);
    }

    @Override
    public FragmentSpread deepCopy() {
        return new FragmentSpread(name, deepCopy(directives));
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
}
