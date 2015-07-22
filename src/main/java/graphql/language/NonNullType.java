package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class NonNullType extends AbstractNode implements Type{

    private Type type;

    public NonNullType() {
    }

    public NonNullType(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(ListType type) {
        this.type = type;
    }

    public void setType(TypeName type) {
        this.type = type;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NonNullType that = (NonNullType) o;

        return !(type != null ? !type.equals(that.type) : that.type != null);

    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "NonNullType{" +
                "type=" + type +
                '}';
    }
}
