package graphql.util;

public class ClassKit {
    public static boolean isClassAvailable(String className) {
        ClassLoader classLoader = ClassKit.class.getClassLoader();
        Class<?> caffieneClass = null;
        try {
            caffieneClass = classLoader.loadClass(className);
        } catch (ClassNotFoundException ignored) {
        }
        return caffieneClass != null;
    }
}
