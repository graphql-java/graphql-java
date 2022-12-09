package graphql.extensions;

import graphql.PublicSpi;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * This interface is a callback asking code to merge two maps with an eye to creating
 * the graphql `extensions` value.
 * <p>
 * How best to merge two maps is hard to know up front.  Should it be a shallow clone or a deep one,
 * should keys be replaced or not and should lists of value be combined?  The {@link ExtensionsMerger} is the
 * interface asked to do this.
 * <p>
 * This interface will be called repeatedly for each change that has been added to the {@link ExtensionsBuilder} and it is expected to merge the two maps as it sees fit
 */
@PublicSpi
public interface ExtensionsMerger {

    /**
     * A default implementation
     */
    ExtensionsMerger DEFAULT = new DefaultExtensionsMerger();

    /**
     * Called to merge the map on the left with the map on the right according to whatever code strategy some-one might envisage
     * <p>
     * The map on the left is guaranteed to have been encountered before the map on the right
     *
     * @param leftMap  the map on the left
     * @param rightMap the map on the right
     *
     * @return a non null merged map
     */
    @NotNull
    Map<Object, Object> merge(@NotNull Map<?, Object> leftMap, @NotNull Map<?, Object> rightMap);
}
