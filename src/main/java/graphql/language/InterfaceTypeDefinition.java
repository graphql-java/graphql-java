package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class InterfaceTypeDefinition extends AbstractNode<InterfaceTypeDefinition> implements TypeDefinition<InterfaceTypeDefinition> {
    private final String name;
    private Description description;
    private final List<FieldDefinition> definitions;
    private final List<Directive> directives;

    public InterfaceTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>());
    }

    public InterfaceTypeDefinition(String name, List<FieldDefinition> definitions, List<Directive> directives) {
        this.name = name;
        this.definitions = definitions;
        this.directives = directives;
    }

    public List<FieldDefinition> getFieldDefinitions() {
        return definitions;
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
        result.addAll(definitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InterfaceTypeDefinition that = (InterfaceTypeDefinition) o;

         return NodeUtil.isEqualTo(this.name,that.name) ;
    }

    @Override
    public InterfaceTypeDefinition deepCopy() {
        return new InterfaceTypeDefinition(name,
                deepCopy(definitions),
                deepCopy(directives)
        );
    }

    @Override
    public String toString() {
        return "InterfaceTypeDefinition{" +
                "name='" + name + '\'' +
                ", fieldDefinitions=" + definitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInterfaceTypeDefinition(this, context);
    }
}
