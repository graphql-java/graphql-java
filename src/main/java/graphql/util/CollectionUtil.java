package graphql.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class CollectionUtil {

	private CollectionUtil() {}

	public static <E> Collection<E> ensureNeverNull(Collection<E> collection) {
		return collection != null ? collection : Collections.emptyList();
	}

	public static <K, V> Map<K, V> ensureNeverNull(Map<K, V> map) {
		return map != null ? map : Collections.emptyMap();
	}
}
