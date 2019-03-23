package cd.connect.app.config;

import net.stickycode.coercion.AbstractNoDefaultCoercion;
import net.stickycode.coercion.Coercion;
import net.stickycode.coercion.CoercionFinder;
import net.stickycode.coercion.CoercionTarget;
import net.stickycode.coercion.StringSpliterable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The Sticky one doesn't trim the names
 * <p></p>
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class MapCoercion extends AbstractNoDefaultCoercion<Map<Object, Object>> {

	private final CoercionFinder finder;

  public MapCoercion(CoercionFinder finder) {
    this.finder = finder;
  }

  @Override
	public Map<Object, Object> coerce(CoercionTarget type, String value) {
		if (value.length() == 0)
			return Collections.emptyMap();


		CoercionTarget[] typeArguments = type.getComponentCoercionTypes();
		assert typeArguments.length == 2 : "Maps should have two type arguments";

		Coercion<?> keyCoercion = findComponentCoercion(typeArguments[0]);
		Coercion<?> valueCoercion = findComponentCoercion(typeArguments[1]);
		Map<Object, Object> map = new HashMap<Object, Object>();
		for (String string : new StringSpliterable(value)) {
			String[] s = string.split("=");
			map.put(
				keyCoercion.coerce(typeArguments[0], s[0].trim()),
				valueCoercion.coerce(typeArguments[1], s[1].trim()));
		}
		return Collections.unmodifiableMap(map);
	}

	private Coercion<?> findComponentCoercion(CoercionTarget target) {
		return finder.find(target);
	}

	@Override
	public boolean isApplicableTo(CoercionTarget target) {
		if (!Map.class.isAssignableFrom(target.getType()))
			return false;

		return true;
	}

}
