package graphql.language;


import graphql.Internal;

import java.util.ArrayList;
import java.util.List;

@Internal
public class OperationTypeDefinition extends AbstractNode {
    private String name;
    private Type type;

    public OperationTypeDefinition() {
        this(null);
    }

    public OperationTypeDefinition(String name) {
        this(name, null);
    }

    public OperationTypeDefinition(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationTypeDefinition that = (OperationTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "OperationTypeDefinition{" +
                "name='" + name + "'" +
                ", type=" + type +
                "}";
    }
}
