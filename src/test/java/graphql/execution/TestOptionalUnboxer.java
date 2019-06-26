package graphql.execution;

import java.util.Map;
import java.util.Optional;

public class TestOptionalUnboxer implements UnboxPossibleOptional.PossibleOptionalUnboxer {
	@Override
	public boolean canUnbox(final Object object) {
		return object instanceof Optional;
	}

	@Override
	public Object unbox(final Object object) {
		return ((Optional)object).orElse(object);
	}
}
