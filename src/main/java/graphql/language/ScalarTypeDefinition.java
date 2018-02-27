package graphql.language;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class ScalarTypeDefinition extends AbstractNode<ScalarTypeDefinition> implements TypeDefinition<ScalarTypeDefinition> {
    private final String name;
    private Description description;
    private final List<Directive> directives;

    public ScalarTypeDefinition(String name) {
        this(name, new ArrayList<>());
    }

    public ScalarTypeDefinition(String name, List<Directive> directives) {
        this.name = name;
        this.directives = directives;
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

    @Override
    public String getName() {
        return name;
    }


    public Description getDescription() {
        return description;
    }

    public void setDescription(Description description) {
        this.description = description;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScalarTypeDefinition that = (ScalarTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public ScalarTypeDefinition deepCopy() {
        return new ScalarTypeDefinition(name, deepCopy(directives));
    }

    @Override
    public String toString() {
        return "ScalarTypeDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visitScalarTypeDefinition(this, data);
    }
}
