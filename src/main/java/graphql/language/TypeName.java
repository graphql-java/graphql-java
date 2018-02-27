package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class TypeName extends AbstractNode<TypeName> implements Type<TypeName> {

    private String name;

    public TypeName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeName that = (TypeName) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public TypeName deepCopy() {
        return new TypeName(name);
    }

    @Override
    public String toString() {
        return "TypeName{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visitTypeName(this, data);
    }
}
