package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class ObjectTypeDefinition extends AbstractNode<ObjectTypeDefinition> implements TypeDefinition<ObjectTypeDefinition> {
    private String name;
    private Description description;
    private final List<Type> implementz;
    private final List<Directive> directives;
    private final List<FieldDefinition> fieldDefinitions;

    public ObjectTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public ObjectTypeDefinition(String name, List<Type> implementz, List<Directive> directives, List<FieldDefinition> fieldDefinitions) {
        this.name = name;
        this.implementz = implementz;
        this.directives = directives;
        this.fieldDefinitions = fieldDefinitions;
    }

    public List<Type> getImplements() {
        return implementz;
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

    public List<FieldDefinition> getFieldDefinitions() {
        return fieldDefinitions;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        result.addAll(implementz);
        result.addAll(directives);
        result.addAll(fieldDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectTypeDefinition that = (ObjectTypeDefinition) o;
        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public ObjectTypeDefinition deepCopy() {
        return new ObjectTypeDefinition(name,
                deepCopy(implementz),
                deepCopy(directives),
                deepCopy(fieldDefinitions)
        );
    }

    @Override
    public String toString() {
        return "ObjectTypeDefinition{" +
                "name='" + name + '\'' +
                ", implements=" + implementz +
                ", directives=" + directives +
                ", fieldDefinitions=" + fieldDefinitions +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitObjectTypeDefinition(this, context);
    }
}
