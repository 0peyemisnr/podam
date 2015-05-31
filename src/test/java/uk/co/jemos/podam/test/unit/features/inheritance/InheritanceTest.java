package uk.co.jemos.podam.test.unit.features.inheritance;

import net.serenitybdd.junit.runners.SerenityRunner;
import net.thucydides.core.annotations.Title;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.test.dto.OneDimensionalChildPojo;
import uk.co.jemos.podam.test.unit.AbstractPodamSteps;
import uk.co.jemos.podam.test.utils.PodamTestConstants;

/**
 * Created by tedonema on 31/05/2015.
 */
@RunWith(SerenityRunner.class)
public class InheritanceTest extends AbstractPodamSteps {

    @Test
    @Title("Podam should handle basic inheritance scenarios")
    public void podamShouldHandleBasicInheritance() throws Exception {

        PodamFactory podamFactory = podamFactorySteps.givenAStandardPodamFactory();
        OneDimensionalChildPojo pojo =
                podamInvocationSteps.whenIInvokeTheFactoryForClass(OneDimensionalChildPojo.class, podamFactory);
        podamValidationSteps.theObjectShouldNotBeNull(pojo);
        int maxValue = PodamTestConstants.NUMBER_INT_TEN;
        podamValidationSteps.theIntFieldShouldHaveValueLessThen(pojo.getParentIntField(), maxValue);
        podamValidationSteps.theCalendarFieldShouldBeValid(pojo.getParentCalendarField());
        podamValidationSteps.theIntFieldShouldNotBeZero(pojo.getIntField());
        podamValidationSteps.theStringFieldCannotBeNullOrEmpty(pojo.getStrField());

    }
}
