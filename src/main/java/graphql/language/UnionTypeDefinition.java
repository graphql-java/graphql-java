package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class UnionTypeDefinition extends AbstractNode<UnionTypeDefinition> implements TypeDefinition<UnionTypeDefinition>, DirectivesContainer<UnionTypeDefinition> {
    private final String name;
    private Description description;
    private final List<Directive> directives;
    private final List<Type> memberTypes;

    public UnionTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>());
    }

    public UnionTypeDefinition(String name, List<Directive> directives, List<Type> memberTypes) {
        this.name = name;
        this.directives = directives;
        this.memberTypes = memberTypes;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public List<Type> getMemberTypes() {
        return memberTypes;
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
        result.addAll(memberTypes);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnionTypeDefinition that = (UnionTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public UnionTypeDefinition deepCopy() {
        return new UnionTypeDefinition(name,
                deepCopy(directives),
                deepCopy(memberTypes)
        );
    }

    @Override
    public String toString() {
        return "UnionTypeDefinition{" +
                "name='" + name + '\'' +
                "directives=" + directives +
                ", memberTypes=" + memberTypes +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitUnionTypeDefinition(this, context);
    }
}
