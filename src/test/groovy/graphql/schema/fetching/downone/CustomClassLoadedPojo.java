package graphql.schema.fetching.downone;

public class CustomClassLoadedPojo {
    final String name;
    final int age;

    public CustomClassLoadedPojo() {
        this.name = "CustomClassLoadedPojo";
        this.age = 42;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

}
