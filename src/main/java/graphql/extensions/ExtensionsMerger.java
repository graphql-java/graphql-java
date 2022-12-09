package graphql.extensions;

import graphql.PublicSpi;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * This interface is a callback asking code to merge two maps with an eye to creating
 * the graphql `extensions` value.
 * <p>
 * How best to merge two maps is hard to know up front.  Should it be a shallow clone or a deep one,
 * should keys be replaced or not and should lists of value be combined?  The ExtensionsMapMerger is the
 * interface asked to do this.
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
     * @return a merged map
     */
    @Nonnull
    Map<Object, Object> merge(@Nonnull Map<?, Object> leftMap, @Nonnull Map<?, Object> rightMap);
}
