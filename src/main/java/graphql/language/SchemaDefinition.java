package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class SchemaDefinition extends AbstractNode<SchemaDefinition> implements Definition<SchemaDefinition> {
    private final List<Directive> directives;
    private final List<OperationTypeDefinition> operationTypeDefinitions;

    public SchemaDefinition() {
        this(new ArrayList<>(), new ArrayList<>());
    }

    public SchemaDefinition(List<Directive> directives, List<OperationTypeDefinition> operationTypeDefinitions) {
        this.directives = directives;
        this.operationTypeDefinitions = operationTypeDefinitions;
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


    public List<OperationTypeDefinition> getOperationTypeDefinitions() {
        return operationTypeDefinitions;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        result.addAll(operationTypeDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchemaDefinition that = (SchemaDefinition) o;

        return true;
    }

    @Override
    public SchemaDefinition deepCopy() {
        return new SchemaDefinition(deepCopy(directives), deepCopy(operationTypeDefinitions));
    }

    @Override
    public String toString() {
        return "SchemaDefinition{" +
                "directives=" + directives +
                ", operationTypeDefinitions=" + operationTypeDefinitions +
                "}";
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitSchemaDefinition(this, context);
    }
}
