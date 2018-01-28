package graphql.language;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class FragmentSpread extends AbstractNode<FragmentSpread> implements Selection<FragmentSpread> {

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

    public Map<String, Directive> getDirectivesByName() {
        return directivesByName(directives);
    }

    public Directive getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
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
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        return result;
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
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visit(this, data);
    }
}
