package graphql.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * You can put any object into the graphql context but often its a map of named things and with
 * Java your are going to have boiler plate cast these lookups a lot.
 *
 * <pre>
 * {@code
 *
 *      Map<String,Object> ctx = dataFetchingEnvironment.getContext()
 *      UserManager userManager = (UserManager) ctx.get("UserManager");
 * }
 * </pre>
 *
 * This container object allows you to cheat by auto casting values for you at the expense of strict type safety.  Its
 * a great compromise between type safety and having to write map like boiler plate.
 *
 * <pre>
 * {@code
 *
 *      EasyObj ctx = dataFetchingEnvironment.getContext()
 *      UserManager userManager = ctx.get("UserManager");
 * }
 * </pre>
 *
 * You can make it a one liner like this
 *
 * <pre>
 * {@code
 *
 *      Foo foo = dataFetchingEnvironment.<EasyObj>getContext().get("Foo");
 * }
 * </pre>
 *
 * Be careful with this class.  Its most useful in and around DataFetchers with their opaque access to Java typing and with
 * JSON map responses.  Don't go crazy with it otherwise its all fun and games until some one looses an eye.
 */
public class EasyObj {
    private final Map<Object, Object> map;

    private EasyObj(Map<Object, Object> map) {
        this.map = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the value held under the key
     *
     * @param key the key to look up
     * @param <T> the type to cast the value to
     *
     * @return a value or null
     */
    public <T> T get(Object key) {
        //noinspection unchecked
        return (T) map.get(key);
    }

    /**
     * @return a builder of EasyObj objects
     */
    public static Builder newObject() {
        return new Builder();
    }

    /**
     * Again this helps create an easy object if you KNOW you have a Map
     * but you don't want to litter your code with boilerplate casts
     *
     * @param mapThing an object that is a Map
     *
     * @return a new EasyObj object
     *
     * @throws java.lang.ClassCastException if its not a Map
     */
    public static EasyObj newObject(Object mapThing) {
        //noinspection unchecked
        return new Builder().putAll((Map<Object, Object>) mapThing).build();
    }

    public static class Builder {
        private final Map<Object, Object> map = new HashMap<>();

        public Builder put(Object key, Object value) {
            map.put(key, value);
            return this;
        }

        public Builder putAll(Map<Object, Object> map) {
            this.map.putAll(map);
            return this;
        }

        public EasyObj build() {
            return new EasyObj(map);
        }
    }
}
