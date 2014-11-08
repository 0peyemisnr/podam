/**
 * 
 */
package uk.co.jemos.podam.test.unit.pdm3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.co.jemos.podam.api.DataProviderStrategy;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;
import uk.co.jemos.podam.test.dto.CollectionExtendingGenericsPojo;
import uk.co.jemos.podam.test.dto.MapExtendingGenericsPojo;
import uk.co.jemos.podam.test.dto.pdm3.Pdm3Pojo;
import uk.co.jemos.podam.test.dto.pdm3.Pdm3PojoConstructor;
import uk.co.jemos.podam.test.dto.pdm3.Pdm3PojoGenericsConstructor;

/**
 * @author Marco Tedone
 * 
 */
public class Pdm3PojoUnitTest {

	public static class TrackingExternalFactory implements PodamFactory {

		List<Class<?>> failures = new ArrayList<Class<?>>();

		@Override
		public <T> T manufacturePojo(Class<T> pojoClass) {
			Type[] noTypes = new Type[0];
			return this.manufacturePojo(pojoClass, noTypes);
		}

		@Override
		public <T> T manufacturePojo(Class<T> pojoClass, Type... genericTypeArgs) {
			failures.add(pojoClass);
			return null;
		}

		@Override
		public DataProviderStrategy getStrategy() {
			return null;
		}
	}

	private static final TrackingExternalFactory trackingFactory
			= new TrackingExternalFactory();

	private static final PodamFactory factory = new PodamFactoryImpl(trackingFactory);

	@Before
	public void start() {
		trackingFactory.failures.clear();
	}

	@After
	public void end() {
		Class<?>[] failures = new Class<?>[trackingFactory.failures.size()];
		trackingFactory.failures.toArray(failures);
		assertEquals(Arrays.toString(failures), 0, failures.length);
	}

	@Test
	public void testPdm3Pojo() {

		Pdm3Pojo pojo = factory.manufacturePojo(Pdm3Pojo.class);
		assertNotNull(pojo);
		assertCollection(pojo.getSomething());
		assertCollection(pojo.getDescendants());
		assertCollection(pojo.getAncestors());
	}

	@Test
	public void testPdm3PojoConstructor() {

		Pdm3PojoConstructor<?> pojo = factory.manufacturePojo(Pdm3PojoConstructor.class, String.class);
		assertNotNull(pojo);
		assertNotNull(pojo.getName());
	}

	@Test
	public void testPdm3ListOfPojos() {

		List<Pdm3PojoConstructor<?>> pojos =
			factory.manufacturePojo(ArrayList.class, Pdm3PojoConstructor.class, String.class);
		assertCollection(pojos);
	}

	@Test
	public void testPdm3MapOfPojos() {

		Map<String, Pdm3PojoConstructor<?>> pojos =
			factory.manufacturePojo(HashMap.class, Integer.class, Pdm3PojoConstructor.class, String.class);
		assertMap(pojos);
	}

	@Test
	public void testPdm3ExtendingListOfPojos() {

		Collection<?> pojos = factory.manufacturePojo(CollectionExtendingGenericsPojo.class);
		assertCollection(pojos);
	}

	@Test
	public void testPdm3ExtendingMapOfPojos() {

		Map<?,?> pojos = factory.manufacturePojo(MapExtendingGenericsPojo.class);
		assertMap(pojos);
	}

	@Test
	public void testPdm3PojoGenericsConstructor() {

		Pdm3PojoGenericsConstructor pojo = factory.manufacturePojo(Pdm3PojoGenericsConstructor.class);
		assertNotNull(pojo);
		assertCollection(pojo.getSomething());
		assertCollection(pojo.getDescendants());
		assertCollection(pojo.getAncestors());
	}

	@Test
	public void testPdm3ListOfGenericPojos() {

		List<Pdm3PojoGenericsConstructor> pojos =
			factory.manufacturePojo(ArrayList.class, Pdm3PojoGenericsConstructor.class);
		assertCollection(pojos);
	}

	@Test
	public void testPdm3MapOfGenericPojos() {

		Map<String, Pdm3PojoGenericsConstructor> pojos =
			factory.manufacturePojo(HashMap.class, String.class, Pdm3PojoGenericsConstructor.class);
		assertMap(pojos);
	}

	private void assertCollection(Collection<?> collection) {

		assertNotNull("The collection should not be null", collection);
		assertFalse("The collection should not be empty", collection.isEmpty());
		for (Object obj : collection) {
			assertNotNull("Collection element should not be null",
				obj);
			if (obj instanceof Pdm3PojoConstructor) {
				Pdm3PojoConstructor<?> pojo = (Pdm3PojoConstructor<?>)obj;
				assertNotNull("Element's field should not be empty", pojo.getName());
				assertEquals("Element's type is String", String.class, pojo.getName().getClass());
			}
		}
	}

	private void assertMap(Map<?,?> map) {

		assertNotNull("The map should not be null", map);
		assertFalse("The map should not be empty", map.isEmpty());
		for (Object key : map.keySet()) {
			assertNotNull("Key should not be empty", key);
			Object value = map.get(key);
			assertNotNull("Value should not be empty", value);
			if (value instanceof Pdm3PojoConstructor) {
				Pdm3PojoConstructor<?> pojo = (Pdm3PojoConstructor<?>)value;
				assertNotNull("Element's field should not be empty", pojo.getName());
				assertEquals("Element's type is String", String.class, pojo.getName().getClass());
			}
		}
	}
}
