package uk.co.jemos.podam.test.unit.steps;

import net.thucydides.core.annotations.Step;
import uk.co.jemos.podam.api.PodamFactory;

/**
 * Created by tedonema on 27/05/2015.
 */
public class PodamInvocationSteps {

    @Step("When I invoke the factory for {0}")
    public <T> T whenIInvokeTheFactoryForClass(Class<T> className, PodamFactory podamFactory) throws Exception {
        return podamFactory.manufacturePojo(className);
    }
}
