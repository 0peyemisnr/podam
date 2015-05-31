package uk.co.jemos.podam.test.unit.steps;

import net.thucydides.core.annotations.Step;
import org.junit.Assert;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by tedonema on 27/05/2015.
 */
public class PodamValidationSteps {

    @Step("Then the Pojo should not be null")
    public boolean thePojoShouldNotBeNull(Object pojo) {
        return pojo == null;
    }

    @Step("Then the Pojo should contain some data")
    public boolean thePojoShouldContainSomeData(Object pojo) {
        return pojo.getClass().getDeclaredFields()[0] != null;
    }

    @Step("Then the Pojo should be null")
    public void thePojoShouldBeNull(Object pojo) {
        Assert.assertNull("The pojo should be null", pojo);
    }

    @Step("Then the inner pojo instance variable should not be null")
    public void theInnerPojoInstanceShouldNotBeNull(Object pojo) {
        Assert.assertNotNull("The inner pojo instance variable should not be null", pojo);
    }

    @Step("Then the int field should not be zero")
    public void theIntFieldShouldNotBeZero(int intField) {
        Assert.assertFalse("The integer field should not be zero", intField == 0);
    }

    @Step("Then the child pojo should not be null")
    public void theChildPojoShouldNotBeNull(Object child) {
        Assert.assertNotNull("The child pojo should not be null", child);
    }

    @Step("Then each of the list elements should not be null")
    public void eachListElementShouldNotBeNull(List<?> list) {
        for (int i = 0; i < list.size(); i++) {
            Assert.assertNotNull(list.get(i));
        }
    }

    @Step("Then each of the map elements should not be null")
    public void eachMapElementShouldNotBeNull(Map<?, ?> map) {

        for (Object mapValue : map.values()) {
            Assert.assertNotNull("The pojo's map element cannot be null!",
                    mapValue);
        }
        
    }

    @Step("Then the calendar object should not be null")
    public void theCalendarFieldShouldNotBeNull(Calendar dateCreated) {
        Assert.assertNotNull("The calendar object should not be null", dateCreated);
    }

    @Step("Then the Date object should not be null")
    public void theDateObjectShouldNotBeNull(Date time) {
        Assert.assertNotNull("The Date object should not be null", time);
    }

    @Step("Then the long[] array should not be null or empty")
    public void theLongArrayShouldNotBeNullOrEmpty(long[] array) {
        Assert.assertNotNull("The given long[] array cannot be null", array);
        Assert.assertTrue("The given long[] array should not be null or empty", array.length > 0);
    }

    @Step("The long value cannot be zero")
    public void theLongValueShouldNotBeZero(long value) {
        Assert.assertTrue("The long value cannot be zero", value > 0);
    }

    @Step("Then any field annotated with @PodamExclude should be null")
    public void anyFieldWithPodamExcludeAnnotationShouldBeNull(Object someObject) {
        Assert.assertNull("The field should be null", someObject);
    }

    @Step("Then the integer field should be greater or equal to zero")
    public void theIntFieldShouldBeGreaterOrEqualToZero(int greaterOrEqualToZeroIntField) {
        Assert.assertTrue("The integer field should be greater or equal to zero", greaterOrEqualToZeroIntField >= 0);
    }

    @Step("Then the integer field {0} should have a value not greater than {1}")
    public void theIntFieldShouldHaveValueNotGreaterThan(int intFieldWithMaxValueOnly, int maxValue) {
        Assert.assertTrue("The int field should have a value <= " + maxValue, intFieldWithMaxValueOnly <= maxValue);
    }

    @Step("Then the integer field {2} should have a value between {0} and {1}")
    public void theIntFieldShouldHaveValueBetween(int minValue, int maxValue, int intFieldWithMinAndMaxValue) {
        Assert.assertTrue("The integer field value " + intFieldWithMinAndMaxValue +
                " should be between " + minValue + " and " + maxValue,
                intFieldWithMinAndMaxValue >= minValue && intFieldWithMinAndMaxValue <= maxValue);
    }

    @Step("Then the integer field should not be null")
    public void theIntegerObjectFieldShouldNotBeNull(Integer integerObjectField) {
        Assert.assertNotNull("The integer object field should not be null", integerObjectField);
    }

    @Step("Then the integer field {0} should have the precise value of {1}")
    public void theIntFieldShouldHaveThePreciseValueOf(int intFieldWithPreciseValue, int preciseValue) {
        Assert.assertTrue("The int field hasn't got a precise value", intFieldWithPreciseValue == preciseValue);
    }
}
