package graphql.schema.bytecode;

import java.util.List;

public class SimplePojo {

    public SimplePojo(List<String> names, int age, Boolean interesting) {
        this.names = names;
        this.age = age;
        this.interesting = interesting;
    }

    List<String> names;

    int age;

    Boolean interesting;

    public List<String> getNames() {
        return names;
    }

    public int getAge() {
        return age;
    }

    public Boolean getInteresting() {
        return interesting;
    }


}
