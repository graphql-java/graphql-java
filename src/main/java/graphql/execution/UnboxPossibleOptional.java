package graphql.execution;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import graphql.Internal;
import graphql.VisibleForTesting;

@Internal
public class UnboxPossibleOptional {

    @VisibleForTesting
    static final List<PossibleOptionalUnboxer> UNBOXERS;

    static {
        final ServiceLoader<PossibleOptionalUnboxer> unboxers = ServiceLoader.load(PossibleOptionalUnboxer.class,
                UnboxPossibleOptional.class.getClassLoader());
        final List<PossibleOptionalUnboxer> listOfUnboxers = new CopyOnWriteArrayList<>();
        unboxers.iterator().forEachRemaining(listOfUnboxers::add);
        UNBOXERS = Collections.unmodifiableList(listOfUnboxers);
    }

    public static Object unboxPossibleOptional(Object result) {
        final Optional<PossibleOptionalUnboxer> unboxer = UNBOXERS.stream()
                .filter(u -> u.canUnbox(result))
                .findFirst();
        if (unboxer.isPresent()) {
            return unboxer.map(u -> u.unbox(result)).orElse(null);
        } else {
            return result;
        }
    }

    /**
     * Java SPI interface for unboxing possible optional types
     */
    public interface PossibleOptionalUnboxer {

        /**
         * Can this unboxer unbox the arg?
         *
         * @param object to try and unbox
         * @return true if it can be unboxed
         */
        boolean canUnbox(final Object object);

        /**
         * Unboxes 'object' if it is boxed in an {@link Optional } like
         * type that this unboxer can handle. Otherwise returns its input
         * unmodified
         *
         * @param object to unbox
         * @return unboxed object, or original if cannot unbox
         */
        Object unbox(final Object object);
    }

    public static class JavaOptionalUnboxer implements PossibleOptionalUnboxer {
        private static final List<Class<?>> SUPPORTED_TYPES;

        static {
            final CopyOnWriteArrayList<Class<?>> classes = new CopyOnWriteArrayList<>();
            classes.add(Optional.class);
            classes.add(OptionalInt.class);
            classes.add(OptionalDouble.class);
            classes.add(OptionalLong.class);

            SUPPORTED_TYPES = Collections.unmodifiableList(classes);
        }

        @Override
        public boolean canUnbox(final Object object) {
            for (Class<?> supportedType : SUPPORTED_TYPES) {
                if (object != null && supportedType.isAssignableFrom(object.getClass())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object unbox(final Object object) {
            if (object instanceof Optional) {
                Optional optional = (Optional) object;
                if (optional.isPresent()) {
                    return optional.get();
                } else {
                    return null;
                }
            } else if (object instanceof OptionalInt) {
                OptionalInt optional = (OptionalInt) object;
                if (optional.isPresent()) {
                    return optional.getAsInt();
                } else {
                    return null;
                }
            } else if (object instanceof OptionalDouble) {
                OptionalDouble optional = (OptionalDouble) object;
                if (optional.isPresent()) {
                    return optional.getAsDouble();
                } else {
                    return null;
                }
            } else if (object instanceof OptionalLong) {
                OptionalLong optional = (OptionalLong) object;
                if (optional.isPresent()) {
                    return optional.getAsLong();
                } else {
                    return null;
                }
            }
            return object;
        }
    }
}
