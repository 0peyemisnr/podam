/**
 * 
 */
package uk.co.jemos.podam.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to customise min and max values on a long type attribute or
 * constructor parameter.
 * 
 * 
 * @author mtedone
 * 
 */
@Target(value = { ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface PodamLongValue {

	/** The minimum value for the annotated field */
	long minValue() default 0;

	/** The minimum value for the annotated field */
	long maxValue() default 0;

	/** It allows clients to make comments */
	String comment() default "";

}
