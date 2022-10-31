package graphql.schema.fetching;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class Pojo {
    final String name;
    final int age;

    public Pojo(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public Integer getHeight() {
        return null;
    }

    public List<String> getOtherNames() {
        return ImmutableList.of("A", "B");
    }

    protected String protectedLevelMethod() {
        return "protectedLevelMethod";
    }

    private String privateLevelMethod() {
        return "privateLevelMethod";
    }

    String packageLevelMethod() {
        return "packageLevelMethod";
    }

    public boolean getInteresting() {
        return false;
    }

    public boolean isInteresting() {
        return true;
    }

    public Boolean getAlone() {
        return true;
    }

    public Boolean getBooleanAndNullish() {
        return null;
    }

    public String get() {
        return "get";
    }

    public String is() {
        return "is";
    }

}