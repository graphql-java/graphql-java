package graphql.language;


public class ListType implements Type{

    private Type type;

    public ListType() {
    }

    public ListType(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ListType listType = (ListType) o;

        return !(type != null ? !type.equals(listType.type) : listType.type != null);

    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ListType{" +
                "type=" + type +
                '}';
    }
}
