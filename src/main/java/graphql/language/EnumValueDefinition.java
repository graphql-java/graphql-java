package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EnumValueDefinition extends AbstractNode<EnumValueDefinition> {
    private final String name;
    private Description description;
    private final List<Directive> directives;

    public EnumValueDefinition(String name) {
        this(name, null);
    }

    public EnumValueDefinition(String name, List<Directive> directives) {
        this.name = name;
        this.directives = (null == directives) ? new ArrayList<>() : directives;
    }

    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
    }

    public void setDescription(Description description) {
        this.description = description;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public Map<String, Directive> getDirectivesByName() {
        return NodeUtil.directivesByName(directives);
    }

    public Directive getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
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

        EnumValueDefinition that = (EnumValueDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);

    }

    @Override
    public EnumValueDefinition deepCopy() {
        return new EnumValueDefinition(name, deepCopy(directives));
    }

    @Override
    public String toString() {
        return "EnumValueDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitEnumValueDefinition(this, context);
    }
}
