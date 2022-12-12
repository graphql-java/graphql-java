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
     * A default implementation will do the following
     * <ul>
     *     <li>It will deep merge the maps</li>
     *     <li>It concatenate lists when they occur under the same key</li>
     *     <li>It will add any keys from the right hand side map that are not present in the left</li>
     *     <li>If a key is in both the left and right side, it will prefer the right hand side</li>
     *     <li>It will try to maintain key order if the maps are ordered</li>
     * </ul>
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
    Map<Object, Object> merge(@NotNull Map<Object, Object> leftMap, @NotNull Map<Object, Object> rightMap);
}
