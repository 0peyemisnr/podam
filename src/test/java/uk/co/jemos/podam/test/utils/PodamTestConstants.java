/**
 * 
 */
package uk.co.jemos.podam.test.utils;

/**
 * Utility class to support testing
 * 
 * @author mtedone
 * 
 */
public class PodamTestConstants {

	// ------------------->> Constants

	/** The minimum value for numeric custom annotations */
	public static final int NUMBER_INT_MIN_VALUE = 0;

	/** A max value for numeric custom annotations */
	public static final int NUMBER_INT_ONE_HUNDRED = 100;

	/** A maximum value for numeric custom annotations */
	public static final int NUMBER_INT_MAX_VALUE = 1000;

	/** The minimum value for float-type numeric custom annotations */
	public static final float NUMBER_FLOAT_MIN_VALUE = 0.0f;

	/** A max value for float-type numeric custom annotations */
	public static final float NUMBER_FLOAT_ONE_HUNDRED = 100.0f;

	/** A maximum value for float-type numeric custom annotations */
	public static final float NUMBER_FLOAT_MAX_VALUE = 1000.0f;

	/** The minimum value for double-type numeric custom annotations */
	public static final double NUMBER_DOUBLE_MIN_VALUE = 0.0;

	/** A max value for float-type numeric custom annotations */
	public static final double NUMBER_DOUBLE_ONE_HUNDRED = 100.0;

	/** A maximum value for float-type numeric custom annotations */
	public static final double NUMBER_DOUBLE_MAX_VALUE = 1000.0;

	/** A precise value for the Podam String annotation */
	public static final String STR_ANNOTATION_PRECISE_VALUE = "preciseStringValue";

	/** The length of the string in the POJO to test String value annotation */
	public static final int STR_ANNOTATION_TWENTY_LENGTH = 20;

	/** The number of elements in an annotated collection */
	public static final int ANNOTATION_COLLECTION_NBR_ELEMENTS = 5;

	// ------------------->> Instance / Static variables

	// ------------------->> Constructors

	/** Non instantiable class */
	private PodamTestConstants() {
		throw new AssertionError();
	}

	// ------------------->> Public methods

	// ------------------->> Getters / Setters

	// ------------------->> Private methods

	// ------------------->> equals() / hashcode() / toString()

	// ------------------->> Inner classes

}
