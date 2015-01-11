/**
 * 
 */
package uk.co.jemos.podam.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * This interface defines the contact for PODAM data providers.
 * <p>
 * PODAM is a tool to fill POJOs with data. There are different requirements
 * when it comes to which data POJOs should be filled with. The default strategy
 * adopted by PODAM is to fill POJOs with random data. However other
 * requirements might dictate to assign deterministic data using sequences, or
 * other predictable sources of data. In order to do so, clients of PODAM will
 * have to provide an implementation of this interface and pass it to the
 * constructor of the {@link PodamFactoryImpl} class.
 * </p>
 * 
 * @author mtedone
 * 
 * @since 1.0.0
 * 
 */
public interface DataProviderStrategy {

	/** It returns a boolean/Boolean value. */
	Boolean getBoolean(AttributeMetadata attributeMetadata);

	/** It returns a byte/Byte value. */
	Byte getByte(AttributeMetadata attributeMetadata);

	/**
	 * It returns a byte/Byte within min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return A byte/Byte within min and max value (included).
	 */
	Byte getByteInRange(byte minValue, byte maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns a char/Character value. */
	Character getCharacter(AttributeMetadata attributeMetadata);

	/**
	 * It returns a char/Character value between min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return A char/Character value between min and max value (included).
	 */
	Character getCharacterInRange(char minValue, char maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns a double/Double value */
	Double getDouble(AttributeMetadata attributeMetadata);

	/**
	 * It returns a double/Double value between min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return A double/Double value between min and max value (included)
	 */
	Double getDoubleInRange(double minValue, double maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns a float/Float value. */
	Float getFloat(AttributeMetadata attributeMetadata);

	/**
	 * It returns a float/Float value between min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return A float/Float value between min and max value (included).
	 */
	Float getFloatInRange(float minValue, float maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns an int/Integer value. */
	Integer getInteger(AttributeMetadata attributeMetadata);

	/**
	 * It returns an int/Integer value between min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return An int/Integer value between min and max value (included).
	 */
	int getIntegerInRange(int minValue, int maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns a long/Long value. */
	Long getLong(AttributeMetadata attributeMetadata);

	/**
	 * It returns a long/Long value between min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return A long/Long value between min and max value (included).
	 */
	Long getLongInRange(long minValue, long maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns a short/Short value. */
	Short getShort(AttributeMetadata attributeMetadata);

	/**
	 * It returns a short/Short value between min and max value (included).
	 * 
	 * @param minValue
	 *            The minimum value for the returned value
	 * @param maxValue
	 *            The maximum value for the returned value
	 * @return A short/Short value between min and max value (included).
	 */
	Short getShortInRange(short minValue, short maxValue,
			AttributeMetadata attributeMetadata);

	/** It returns a string value */
	String getStringValue(AttributeMetadata attributeMetadata);

	/**
	 * It returns a String of {@code length} characters.
	 * 
	 * @param length
	 *            The number of characters required in the returned String
	 * @return A String of {@code length} characters
	 */
	String getStringOfLength(int length, AttributeMetadata attributeMetadata);

	/**
	 * Returns the number of default collection elements.
	 * <p>
	 * Implementations of this interface need to provide this value.
	 * </p>
	 * 
	 * @param type
	 *            Type of collection's elements
	 * @return The number of default collection elements
	 */
	int getNumberOfCollectionElements(Class<?> type);

	/**
	 * Returns how many times it is allowed to PODAM to create an instance
	 * of the same class in a recursive hierarchy
	 * <p>
	 * Implementations of this interface need to provide this value.
	 * </p>
	 * 
	 * @param type
	 *            Type of POJO to be created
	 * @return How many times it is allowed to create an instance
	 *         of the same class in a recursive hierarchy
	 */
	int getMaxDepth(Class<?> type);

	/**
	 * Returns whether the memoization setting is enabled.
	 *
	 * When memoization is enabled, only one object will be created for each type. Every next property of the same type
	 * will be a reference to the same object.
	 * This can dramatically improve performance but with the expense of not having objects with different values.
	 *
	 * <p>
	 * Implementations of this interface need to provide this value.
	 * </p>
	 *
	 * @return Whether the memoization setting is enabled.
	 */
	boolean isMemoizationEnabled();

	/**
	 * Rearranges POJO's constructors in order they will be tried to
	 * produce the POJO.
	 * Default strategy consists of putting constructors with less parameters
	 * to be tried first.
	 * 
	 * @param constructors
	 *            Array of POJO's constructors
	 */
	void sort(Constructor<?>[] constructors);

	/**
	 * Rearranges POJO's methods in order they will be tried to
	 * produce the POJO.
	 * Default strategy consists of putting factory methods with more parameters
	 * to be tried first.
	 * 
	 * @param methods
	 *            Array of POJO's methods
	 */
	void sort(Method[] methods);

	/**
	 * Resolves abstract classes and interfaces.
	 * <p>
	 * Should return specific class type, which can be instantiated and assigned
	 * to abstract class type or interface.
	 * </p>
	 * 
	 * @param nonInstantiatableClass
	 *            Abstract class type or interface
	 * @return Non-abstract class type derived from
	 *         {@code nonInstantiatableClass}.
	 */
	<T> Class<? extends T> getSpecificClass(Class<T> nonInstantiatableClass);

	/**
	 * Identifies {@link Annotation}s for fields to be skipped.
	 * <p>
	 * Should return set of annotations, which will be treated as notion for
	 * {@link PodamFactory} to skip production of these particular fields.
	 * </p>
	 * 
	 * @return set of annotations, which mark fields to be skipped from populating.
	 */
	Set<Class<? extends Annotation>> getExcludedAnnotations();
}
