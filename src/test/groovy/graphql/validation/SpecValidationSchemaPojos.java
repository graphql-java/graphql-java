package graphql.validation;

/**
 * Sample schema pojos used in the spec for validation examples
 * http://facebook.github.io/graphql/#sec-Validation
 *
 * @author dwinsor
 */
@SuppressWarnings("ClassCanBeStatic")
public class SpecValidationSchemaPojos {
    public class Human {
        public String name;
    }

    public class Alien {
        public String name;
    }

    public class Dog {
        public String name;
        public String nickname;
        public int barkVolume;
        public boolean doesKnowCommand;
        public boolean isHousetrained;
        public Human owner;
    }

    public class Cat {
        public String name;
        public String nickname;
        public int meowVolume;
        public boolean doesKnowCommand;
    }

    public class QueryRoot {
        public Dog dog;
    }
}
