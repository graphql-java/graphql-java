package graphql;


public class Assert {


    public static void assertNotNull(Object object, String errorMessage) {
        if (object != null) return;
        throw new AssertException(errorMessage);
    }

}
