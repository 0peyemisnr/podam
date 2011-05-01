/**
 * 
 */
package uk.co.jemos.podam.api;

import java.util.Random;

import uk.co.jemos.podam.utils.PodamConstants;

/**
 * Default implementation of a {@link DataProviderStrategy}
 * <p>
 * This default implementation returns values based on a random generator.
 * <b>Don't use this implementation if you seek deterministic values</b>
 * </p>
 * 
 * <p>
 * All values returned by this implementation are <b>different from zero</b>.
 * </p>
 * 
 * <p>
 * This implementation is a Singleton
 * </p>
 * 
 * @author mtedone
 * 
 */

public class RandomDataProviderStrategy implements DataProviderStrategy {

	// ------------------->> Constants

	/** A RANDOM generator */
	private static final Random RANDOM = new Random(System.currentTimeMillis());

	/** The singleton instance of this implementation */
	private static final RandomDataProviderStrategy SINGLETON = new RandomDataProviderStrategy();

	/** Application logger */
	private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger
			.getLogger(RandomDataProviderStrategy.class.getName());

	// ------------------->> Instance / Static variables

	// ------------------->> Constructors

	/**
	 * Implementation of the Singleton pattern
	 */
	private RandomDataProviderStrategy() {
	}

	// ------------------->> Public methods

	/**
	 * Implementation of the Singleton pattern
	 * 
	 * @return A singleton instance of this class
	 */
	public static RandomDataProviderStrategy getInstance() {
		return SINGLETON;
	}

	/**
	 * {@inheritDoc}
	 */
	public Boolean getBoolean() {
		return ((this.getInteger() % 2) == 0 ? true : false);
	}

	/**
	 * {@inheritDoc}
	 */
	public Byte getByte() {
		byte nextByte = (byte) RANDOM.nextInt(Byte.MAX_VALUE);
		while (nextByte == 0) {
			nextByte = (byte) RANDOM.nextInt(Byte.MAX_VALUE);
		}
		return nextByte;
	}

	/**
	 * {@inheritDoc}
	 */
	public Byte getByteInRange(byte minValue, byte maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		byte retValue = (byte) (minValue + (byte) (Math.random() * ((maxValue - minValue) + 1)));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = (byte) (minValue + (byte) (Math.random() * ((maxValue - minValue) + 1)));
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Character getCharacter() {
		return (char) this.getInteger().intValue();

	}

	/**
	 * {@inheritDoc}
	 */
	public Character getCharacterInRange(char minValue, char maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		char retValue = (char) (minValue + (char) (Math.random() * ((maxValue - minValue) + 1)));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = (char) (minValue + (char) (Math.random() * ((maxValue - minValue) + 1)));
		}

		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Double getDouble() {
		double retValue = RANDOM.nextDouble();
		while (retValue == 0.0) {
			retValue = RANDOM.nextDouble();
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Double getDoubleInRange(double minValue, double maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		double retValue = minValue
				+ (Math.random() * ((maxValue - minValue) + 1));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = minValue + (Math.random() * ((maxValue - minValue) + 1));
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Float getFloat() {
		float retValue = RANDOM.nextFloat();
		while (retValue == 0.0f) {
			retValue = RANDOM.nextFloat();
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Float getFloatInRange(float minValue, float maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		float retValue = minValue
				+ (float) (Math.random() * ((maxValue - minValue) + 1));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = minValue
					+ (float) (Math.random() * ((maxValue - minValue) + 1));
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Integer getInteger() {
		Integer retValue = RANDOM.nextInt();
		while (retValue.intValue() == 0) {
			retValue = RANDOM.nextInt();
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getIntegerInRange(int minValue, int maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		int retValue = minValue
				+ (int) (Math.random() * ((maxValue - minValue) + 1));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = minValue
					+ (int) (Math.random() * ((maxValue - minValue) + 1));
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Long getLong() {
		Long retValue = RANDOM.nextLong();
		while (retValue.longValue() == 0L) {
			retValue = RANDOM.nextLong();
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Long getLongInRange(long minValue, long maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		long retValue = minValue
				+ (long) (Math.random() * ((maxValue - minValue) + 1));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = minValue
					+ (long) (Math.random() * ((maxValue - minValue) + 1));
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Short getShort() {
		short retValue = (short) RANDOM.nextInt(Byte.MAX_VALUE);
		while (retValue == 0) {
			retValue = (short) RANDOM.nextInt(Byte.MAX_VALUE);
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Short getShortInRange(short minValue, short maxValue) {
		// This can happen. It's a way to specify a precise value
		if (minValue == maxValue) {
			return minValue;
		}
		short retValue = (short) (minValue + (short) (Math.random() * ((maxValue - minValue) + 1)));
		while ((retValue < minValue) || (retValue > maxValue)) {
			retValue = (short) (minValue + (short) (Math.random() * ((maxValue - minValue) + 1)));
		}
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getStringValue() {
		return this.getStringOfLength(PodamConstants.STR_DEFAULT_LENGTH);
	}

	/**
	 * {@inheritDoc}
	 */
	public String getStringOfLength(int length) {

		StringBuilder buff = new StringBuilder(
				PodamConstants.STR_DEFAULT_ENCODING);
		// Default length was 5 for some reason
		buff.setLength(0);

		while (buff.length() < length) {
			buff.append(this.getCharacter());
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("Length of returned string: " + buff.toString().length());
		}

		return buff.toString();

	}

	// ------------------->> Getters / Setters

	// ------------------->> Private methods

	// ------------------->> equals() / hashcode() / toString()

	// ------------------->> Inner classes

}
