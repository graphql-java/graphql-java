package graphql.language;


public class Argument implements Node{

    private String name;
    private Value value;

    public Argument(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Argument argument = (Argument) o;

        if (name != null ? !name.equals(argument.name) : argument.name != null) return false;
        return !(value != null ? !value.equals(argument.value) : argument.value != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
