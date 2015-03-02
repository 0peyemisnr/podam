package uk.co.jemos.podam.test.unit;

import org.junit.Assert;
import org.junit.Test;

import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;
import uk.co.jemos.podam.test.dto.ReadOnlyComplexTypesPojo;
import uk.co.jemos.podam.test.utils.PodamTestUtils;

/**
 * @author daivanov
 *
 */
public class ReadOnlyComplexTypesTest {

	private static final PodamFactory factory = new PodamFactoryImpl();

	@Test
	public void testReadOnlyComplexTypesPojoInstantiation() {
		ReadOnlyComplexTypesPojo pojo = factory.manufacturePojo(ReadOnlyComplexTypesPojo.class);
		Assert.assertNotNull("Manufacturing failed", pojo);
		Assert.assertNotNull("List should be present", pojo.getList());
		Assert.assertNotNull("Map should be present", pojo.getMap());
		Assert.assertNotNull("Complex element should be present", pojo.getValue());
		PodamTestUtils.assertListElementsType(pojo.getList(), Integer.class);
		PodamTestUtils.assertMapElementsType(pojo.getMap(), Long.class, String.class);
		Assert.assertNotNull("Complex element should not be empty",
				pojo.getValue().getValue());
	}
}
