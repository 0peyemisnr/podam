/**
 *
 */
package uk.co.jemos.podam.api;

import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.jemos.podam.common.*;
import uk.co.jemos.podam.exceptions.PodamMockeryException;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The PODAM factory implementation
 *
 * @author mtedone
 *
 * @since 1.0.0
 *
 */
@ThreadSafe
@Immutable
public class PodamFactoryImpl implements PodamFactory {

	// ------------------->> Constants

	private static final String RESOLVING_COLLECTION_EXCEPTION_STR = "An exception occurred while resolving the collection";

	private static final String MAP_CREATION_EXCEPTION_STR = "An exception occurred while creating a Map object";

	private static final String UNCHECKED_STR = "unchecked";

	private static final String THE_ANNOTATION_VALUE_STR = "The annotation value: ";

	private static final Type[] NO_TYPES = new Type[0];

	private static final Object[] NO_ARGS = new Object[0];

	private static final Object OBJECT = new Object();

	/** Application logger */
	private static final Logger LOG = LoggerFactory
			.getLogger(PodamFactoryImpl.class.getName());

	// ------------------->> Instance / variables

	/**
	 * External factory to delegate production this factory cannot handle
	 * <p>
	 * The default is {@link NullExternalFactory}.
	 * </p>
	 */
	private final PodamFactory externalFactory;

	/**
	 * The strategy to use to fill data.
	 * <p>
	 * The default is {@link RandomDataProviderStrategy}.
	 * </p>
	 */
	private final DataProviderStrategy strategy;

	/**
	 * The strategy to use to introspect data.
	 * <p>
	 * The default is {@link DefaultClassInfoStrategy}.
	 * </p>
	 */
	private ClassInfoStrategy classInfoStrategy
			= DefaultClassInfoStrategy.getInstance();

	// ------------------->> Constructors

	/**
	 * Default constructor.
	 */
	public PodamFactoryImpl() {
		this(NullExternalFactory.getInstance(),
				RandomDataProviderStrategy.getInstance());
	}

	/**
	 * Constructor with non-default strategy
	 *
	 * @param strategy
	 *            The strategy to use to fill data
	 */
	public PodamFactoryImpl(DataProviderStrategy strategy) {

		this(NullExternalFactory.getInstance(), strategy);
	}

	/**
	 * Constructor with non-default external factory
	 *
	 * @param externalFactory
	 *            External factory to delegate production this factory cannot
	 *            handle
	 */
	public PodamFactoryImpl(PodamFactory externalFactory) {
		this(externalFactory, RandomDataProviderStrategy.getInstance());
	}

	/**
	 * Full constructor.
	 *
	 * @param externalFactory
	 *            External factory to delegate production this factory cannot
	 *            handle
	 * @param strategy
	 *            The strategy to use to fill data
	 */
	public PodamFactoryImpl(PodamFactory externalFactory,
			DataProviderStrategy strategy) {
		this.externalFactory = externalFactory;
		this.strategy = strategy;
	}

	// ------------------->> Public methods

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T manufacturePojo(Class<T> pojoClass) {
		return this.manufacturePojo(pojoClass, NO_TYPES);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T manufacturePojoWithFullData(Class<T> pojoClass, Type... genericTypeArgs) {
		AbstractRandomDataProviderStrategy sortingStrategy;
		if (getStrategy() instanceof AbstractRandomDataProviderStrategy) {
			sortingStrategy = (AbstractRandomDataProviderStrategy) getStrategy();
		} else {
			throw new IllegalStateException("manufacturePojoWithFullData can"
					+ " only be used with strategies derived from"
					+ " AbstractRandomDataProviderStrategy");
		}
		ConstructorAdaptiveComparator constructorComparator;
		if (sortingStrategy.getConstructorComparator()
				instanceof ConstructorAdaptiveComparator) {
			constructorComparator = (ConstructorAdaptiveComparator)
					sortingStrategy.getConstructorComparator();
		} else {
			throw new IllegalStateException("manufacturePojoWithFullData can"
					+ " only be used with constructor comparators derived from"
					+ " ConstructorAdaptiveComparator");
		}
		constructorComparator.addHeavyClass(pojoClass);
		T retValue = this.manufacturePojo(pojoClass, genericTypeArgs);
		constructorComparator.removeHeavyClass(pojoClass);
		return retValue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T manufacturePojo(Class<T> pojoClass, Type... genericTypeArgs) {
		Map<Class<?>, Integer> pojos = new HashMap<Class<?>, Integer>();
		pojos.put(pojoClass, 0);
		try {
			Class<?> declaringClass = null;
			AttributeMetadata pojoMetadata = new AttributeMetadata(pojoClass,
					genericTypeArgs, declaringClass);
			return this.manufacturePojoInternal(pojoClass, pojoMetadata,
					pojos, genericTypeArgs);
		} catch (InstantiationException e) {
			throw new PodamMockeryException(e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new PodamMockeryException(e.getMessage(), e);
		} catch (InvocationTargetException e) {
			throw new PodamMockeryException(e.getMessage(), e);
		} catch (ClassNotFoundException e) {
			throw new PodamMockeryException(e.getMessage(), e);
		}
	}

	// ------------------->> Getters / Setters

	/**
	 * {@inheritDoc}
	 */

	@Override
	public DataProviderStrategy getStrategy() {
		return strategy;
	}

	// ------------------->> Private methods

	/**
	 * Fills type agruments map
	 * <p>
	 * This method places required and provided types for object creation into a
	 * map, which will be used for type mapping.
	 * </p>
	 *
	 * @param typeArgsMap
	 *            a map to fill
	 * @param pojoClass
	 *            Typed class
	 * @param genericTypeArgs
	 *            Type arguments provided for a generics object by caller
	 * @return Array of unused provided generic type arguments
	 * @throws IllegalStateException
	 *             If number of typed parameters doesn't match number of
	 *             provided generic types
	 */
	private Type[] fillTypeArgMap(final Map<String, Type> typeArgsMap,
			final Class<?> pojoClass, final Type[] genericTypeArgs) {

		TypeVariable<?>[] array = pojoClass.getTypeParameters();
		List<TypeVariable<?>> typeParameters = new ArrayList<TypeVariable<?>>(Arrays.asList(array));
		Iterator<TypeVariable<?>> iterator = typeParameters.iterator();
		/* Removing types, which are already in typeArgsMap */
		while (iterator.hasNext()) {
			if (typeArgsMap.containsKey(iterator.next().getName())) {
				iterator.remove();
			}
		}

		if (typeParameters.size() > genericTypeArgs.length) {
			String msg = pojoClass.getCanonicalName()
					+ " is missing generic type arguments, expected "
					+ typeParameters + " found "
					+ Arrays.toString(genericTypeArgs);
			throw new IllegalStateException(msg);
		}

		int i;
		for (i = 0; i < typeParameters.size(); i++) {
			typeArgsMap.put(typeParameters.get(i).getName(), genericTypeArgs[i]);
		}
		Type[] genericTypeArgsExtra;
		if (typeParameters.size() < genericTypeArgs.length) {
			genericTypeArgsExtra = Arrays.copyOfRange(genericTypeArgs, i,
					genericTypeArgs.length);
		} else {
			genericTypeArgsExtra = null;
		}

		/* Adding types, which were specified during inheritance */
		Class<?> clazz = pojoClass;
		while (clazz != null) {
			Type superType = clazz.getGenericSuperclass();
			clazz = clazz.getSuperclass();
			if (superType instanceof ParameterizedType) {
				ParameterizedType paramType = (ParameterizedType) superType;
				Type[] actualParamTypes = paramType.getActualTypeArguments();
				TypeVariable<?>[] paramTypes = clazz.getTypeParameters();
				for (i = 0; i < actualParamTypes.length
						&& i < paramTypes.length; i++) {
					if (actualParamTypes[i] instanceof Class) {
						typeArgsMap.put(paramTypes[i].getName(),
								actualParamTypes[i]);
					}
				}
			}
		}

		return genericTypeArgsExtra;
	}

	/**
	 * It attempts to create an instance of the given class
	 * <p>
	 * This method attempts to create an instance of the given argument for
	 * classes without setters. These may be either immutable classes (e.g. with
	 * final attributes and no setters) or Java classes (e.g. belonging to the
	 * java / javax namespace). In case the class does not provide a public,
	 * no-arg constructor (e.g. Calendar), this method attempts to find a ,
	 * no-args, factory method (e.g. getInstance()) and it invokes it
	 * </p>
	 *
	 * @param pojoClass
	 *            The name of the class for which an instance filled with values
	 *            is required
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 *
	 *
	 * @return An instance of the given class
	 * @throws IllegalArgumentException
	 *             If an illegal argument was passed to the constructor
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 */
	private Object instantiatePojoWithoutConstructors(
			Class<?> pojoClass, Map<Class<?>, Integer> pojos,
			Type... genericTypeArgs) throws InstantiationException,
			IllegalAccessException, InvocationTargetException,
			ClassNotFoundException {

		Object retValue = null;

		final Map<String, Type> typeArgsMap = new HashMap<String, Type>();
		Type[] genericTypeArgsExtra = fillTypeArgMap(typeArgsMap,
					pojoClass, genericTypeArgs);

		// If no publicly accessible constructors are available,
		// the best we can do is to find a constructor (e.g.
		// getInstance())

		Method[] declaredMethods = pojoClass.getDeclaredMethods();
		strategy.sort(declaredMethods);

		// A candidate factory method is a method which returns the
		// Class type

		// The parameters to pass to the method invocation
		Object[] parameterValues = null;
		Object[] noParams = new Object[] {};

		for (Method candidateConstructor : declaredMethods) {

			if (!Modifier.isStatic(candidateConstructor.getModifiers())
					|| !candidateConstructor.getReturnType().equals(pojoClass)
					|| retValue != null) {
				continue;
			}

			parameterValues = new Object[candidateConstructor
					.getParameterTypes().length];

			Class<?>[] parameterTypes = candidateConstructor.getParameterTypes();

			if (parameterTypes.length == 0) {

				parameterValues = noParams;

			} else {

				// This is a factory method with arguments

				Annotation[][] parameterAnnotations = candidateConstructor
						.getParameterAnnotations();

				int idx = 0;

				for (Class<?> parameterType : parameterTypes) {

					List<Annotation> annotations = Arrays
							.asList(parameterAnnotations[idx]);
					Type genericType = candidateConstructor.getGenericParameterTypes()[idx];

					parameterValues[idx] = manufactureParameterValue(parameterType,
							genericType, annotations, typeArgsMap, pojos,
							genericTypeArgsExtra == null ? NO_TYPES : genericTypeArgsExtra);

					idx++;

				}

			}

			try {

				retValue = candidateConstructor.invoke(pojoClass,
						parameterValues);
				LOG.debug("Could create an instance using "
						+ candidateConstructor);

			} catch (Exception t) {

				LOG.debug(
						"PODAM could not create an instance for constructor: "
								+ candidateConstructor
								+ ". Will try another one...", t);

			}

		}

		if (retValue == null) {
			LOG.debug("For class {} PODAM could not possibly create"
					+ " a value statically. Will try other means.",
					pojoClass);
		}

		return retValue;

	}

	/**
	 * It resolves generic parameter type
	 *
	 *
	 * @param paramType
	 *            The generic parameter type
	 * @param typeArgsMap
	 *            A map of resolved types
	 * @param methodGenericTypeArgs
	 *            Return value posible generic types of the generic parameter
	 *            type
	 * @return value for class representing the generic parameter type
	 */
	private Class<?> resolveGenericParameter(Type paramType,
			Map<String, Type> typeArgsMap,
			AtomicReference<Type[]> methodGenericTypeArgs) {

		Class<?> parameterType = null;
		methodGenericTypeArgs.set(NO_TYPES);
		if (paramType instanceof TypeVariable<?>) {
			final TypeVariable<?> typeVariable = (TypeVariable<?>) paramType;
			final Type type = typeArgsMap.get(typeVariable.getName());
			if (type != null) {
				parameterType = resolveGenericParameter(type, typeArgsMap,
						methodGenericTypeArgs);
			}
		} else if (paramType instanceof ParameterizedType) {
			ParameterizedType pType = (ParameterizedType) paramType;
			parameterType = (Class<?>) pType.getRawType();
			methodGenericTypeArgs.set(pType.getActualTypeArguments());
		} else if (paramType instanceof WildcardType) {
			WildcardType wType = (WildcardType) paramType;
			Type[] bounds = wType.getLowerBounds();
			String msg;
			if (bounds != null && bounds.length > 0) {
				msg = "Lower bounds:";
			} else {
				bounds = wType.getUpperBounds();
				msg = "Upper bounds:";
			}
			if (bounds != null && bounds.length > 0) {
				LOG.debug(msg + Arrays.toString(bounds));
				parameterType = resolveGenericParameter(bounds[0], typeArgsMap,
						methodGenericTypeArgs);
			}
		} else if (paramType instanceof Class) {
			parameterType = (Class<?>) paramType;
		}

		if (parameterType == null) {
			LOG.warn("Unrecognized type {}. Will use Object instead",
					paramType);
			parameterType = Object.class;
		}
		return parameterType;
	}

	/**
	 * It resolves and returns the primitive value depending on the type
	 *
	 *
	 * @param primitiveClass
	 *            The primitive type class
	 * @param annotations
	 *            The annotations to consider for this attribute
	 * @param attributeMetadata
	 * @return the primitive value depending on the type
	 *
	 * @throws IllegalArgumentException
	 *             If a specific value was set in an annotation but it was not
	 *             possible to convert such value in the desired type
	 */
	private Object resolvePrimitiveValue(Class<?> primitiveClass,
			List<Annotation> annotations, AttributeMetadata attributeMetadata) {

		Object retValue = null;

		if (primitiveClass.equals(int.class)) {

			retValue = getIntegerValueWithinRange(annotations, attributeMetadata);

		} else if (primitiveClass.equals(long.class)) {

			retValue = getLongValueWithinRange(annotations, attributeMetadata);

		} else if (primitiveClass.equals(float.class)) {

			retValue = getFloatValueWithinRange(annotations, attributeMetadata);

		} else if (primitiveClass.equals(double.class)) {

			retValue = getDoubleValueWithinRange(annotations, attributeMetadata);

		} else if (primitiveClass.equals(boolean.class)) {

			retValue = getBooleanValueForAnnotation(annotations, attributeMetadata);

		} else if (primitiveClass.equals(byte.class)) {

			retValue = getByteValueWithinRange(annotations, attributeMetadata);

		} else if (primitiveClass.equals(short.class)) {

			retValue = getShortValueWithinRange(annotations, attributeMetadata);

		} else if (primitiveClass.equals(char.class)) {

			retValue = getCharacterValueWithinRange(annotations, attributeMetadata);

		} else {

			throw new IllegalArgumentException(
					String.format("%s is unsupported primitive type",
							primitiveClass));
		}

		return retValue;
	}

	/**
	 * It returns the boolean value indicated in the annotation.
	 *
	 * @param annotations
	 *            The collection of annotations for the annotated attribute
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 * @return The boolean value indicated in the annotation
	 */
	private Boolean getBooleanValueForAnnotation(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {

		Boolean retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamBooleanValue.class.isAssignableFrom(annotation.getClass())) {
				PodamBooleanValue localStrategy = (PodamBooleanValue) annotation;
				retValue = localStrategy.boolValue();

				break;
			}
		}

		if (retValue == null) {
			retValue = strategy.getBoolean(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * It returns a random byte if the attribute was annotated with
	 * {@link PodamByteValue} or {@code null} otherwise
	 *
	 * @param annotations
	 *            The list of annotations for this attribute
	 *
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 *
	 * @return A random byte if the attribute was annotated with
	 *
	 * @throws IllegalArgumentException
	 *             If the {@link PodamByteValue#numValue()} value has been set
	 *             and it is not convertible to a byte type
	 */
	private Byte getByteValueWithinRange(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {
		Byte retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamByteValue.class.isAssignableFrom(annotation.getClass())) {
				PodamByteValue intStrategy = (PodamByteValue) annotation;

				String numValueStr = intStrategy.numValue();
				if (null != numValueStr && !"".equals(numValueStr)) {
					try {

						retValue = Byte.valueOf(numValueStr);

					} catch (NumberFormatException nfe) {
						String errMsg = "The precise value: "
								+ numValueStr
								+ " cannot be converted to a byte type. An exception will be thrown.";
						LOG.error(errMsg);
						throw new IllegalArgumentException(errMsg, nfe);
					}
				} else {
					byte minValue = intStrategy.minValue();
					byte maxValue = intStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getByteInRange(minValue, maxValue,
							attributeMetadata);
				}

				break;

			}
		}

		if (retValue == null) {
			retValue = strategy.getByte(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * It returns a random short if the attribute was annotated with
	 * {@link PodamShortValue} or {@code null} otherwise
	 *
	 * @param annotations
	 *            The annotations with which the attribute was annotated
	 *
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 *
	 * @return A random short if the attribute was annotated with
	 *         {@link PodamShortValue} or {@code null} otherwise
	 *
	 * @throws IllegalArgumentException
	 *             If {@link PodamShortValue#numValue()} was set and its value
	 *             could not be converted to a Short type
	 */
	private Short getShortValueWithinRange(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {

		Short retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamShortValue.class.isAssignableFrom(annotation.getClass())) {
				PodamShortValue shortStrategy = (PodamShortValue) annotation;

				String numValueStr = shortStrategy.numValue();
				if (null != numValueStr && !"".equals(numValueStr)) {
					try {
						retValue = Short.valueOf(numValueStr);
					} catch (NumberFormatException nfe) {
						String errMsg = "The precise value: "
								+ numValueStr
								+ " cannot be converted to a short type. An exception will be thrown.";
						LOG.error(errMsg);
						throw new IllegalArgumentException(errMsg, nfe);
					}
				} else {

					short minValue = shortStrategy.minValue();
					short maxValue = shortStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getShortInRange(minValue, maxValue,
							attributeMetadata);

				}

				break;

			}
		}

		if (retValue == null) {
			retValue = strategy.getShort(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * It creates and returns a random {@link Character} value
	 *
	 * @param annotations
	 *            The list of annotations which might customise the return value
	 *
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 *
	 * @return A random {@link Character} value
	 */
	private Character getCharacterValueWithinRange(
			List<Annotation> annotations, AttributeMetadata attributeMetadata) {

		Character retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamCharValue.class.isAssignableFrom(annotation.getClass())) {
				PodamCharValue annotationStrategy = (PodamCharValue) annotation;

				char charValue = annotationStrategy.charValue();
				if (charValue != ' ') {
					retValue = charValue;

				} else {

					char minValue = annotationStrategy.minValue();
					char maxValue = annotationStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getCharacterInRange(minValue, maxValue,
							attributeMetadata);

				}

				break;

			}
		}

		if (retValue == null) {
			retValue = strategy.getCharacter(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * Returns either a customised int value if a {@link PodamIntValue}
	 * annotation was provided or a random integer if this was not the case
	 *
	 * @param annotations
	 *            The list of annotations for the int attribute
	 *
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 *
	 * @return Either a customised int value if a {@link PodamIntValue}
	 *         annotation was provided or a random integer if this was not the
	 *         case
	 *
	 * @throws IllegalArgumentException
	 *             If it was not possible to convert the
	 *             {@link PodamIntValue#numValue()} to an Integer
	 */
	private Integer getIntegerValueWithinRange(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {

		Integer retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamIntValue.class.isAssignableFrom(annotation.getClass())) {
				PodamIntValue intStrategy = (PodamIntValue) annotation;

				String numValueStr = intStrategy.numValue();
				if (null != numValueStr && !"".equals(numValueStr)) {
					try {
						retValue = Integer.valueOf(numValueStr);
					} catch (NumberFormatException nfe) {
						String errMsg = THE_ANNOTATION_VALUE_STR
								+ numValueStr
								+ " could not be converted to an Integer. An exception will be thrown.";
						LOG.error(errMsg);
						throw new IllegalArgumentException(errMsg, nfe);

					}

				} else {

					int minValue = intStrategy.minValue();
					int maxValue = intStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getIntegerInRange(minValue, maxValue,
							attributeMetadata);

				}

				break;

			}

		}

		if (retValue == null) {
			retValue = strategy.getInteger(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * Returns either a customised float value if a {@link PodamFloatValue}
	 * annotation was provided or a random float if this was not the case
	 *
	 * @param annotations
	 *            The list of annotations for the int attribute
	 *
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 *
	 *
	 * @return Either a customised float value if a {@link PodamFloatValue}
	 *         annotation was provided or a random float if this was not the
	 *         case
	 *
	 * @throws IllegalArgumentException
	 *             If {@link PodamFloatValue#numValue()} contained a value not
	 *             convertible to a Float type
	 */
	private Float getFloatValueWithinRange(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {

		Float retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamFloatValue.class.isAssignableFrom(annotation.getClass())) {
				PodamFloatValue floatStrategy = (PodamFloatValue) annotation;

				String numValueStr = floatStrategy.numValue();
				if (null != numValueStr && !"".equals(numValueStr)) {
					try {
						retValue = Float.valueOf(numValueStr);
					} catch (NumberFormatException nfe) {
						String errMsg = THE_ANNOTATION_VALUE_STR
								+ numValueStr
								+ " could not be converted to a Float. An exception will be thrown.";
						LOG.error(errMsg);
						throw new IllegalArgumentException(errMsg, nfe);
					}
				} else {

					float minValue = floatStrategy.minValue();
					float maxValue = floatStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getFloatInRange(minValue, maxValue,
							attributeMetadata);

				}

				break;

			}

		}

		if (retValue == null) {
			retValue = strategy.getFloat(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * It creates and returns a random {@link Double} value
	 *
	 * @param annotations
	 *            The list of annotations which might customise the return value
	 *
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation *
	 *
	 * @return a random {@link Double} value
	 */
	private Double getDoubleValueWithinRange(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {

		Double retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamDoubleValue.class.isAssignableFrom(annotation.getClass())) {
				PodamDoubleValue doubleStrategy = (PodamDoubleValue) annotation;

				String numValueStr = doubleStrategy.numValue();
				if (null != numValueStr && !"".equals(numValueStr)) {

					try {
						retValue = Double.valueOf(numValueStr);
					} catch (NumberFormatException nfe) {
						String errMsg = THE_ANNOTATION_VALUE_STR
								+ numValueStr
								+ " could not be converted to a Double. An exception will be thrown.";
						LOG.error(errMsg);
						throw new IllegalArgumentException(errMsg, nfe);
					}

				} else {

					double minValue = doubleStrategy.minValue();
					double maxValue = doubleStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getDoubleInRange(minValue, maxValue,
							attributeMetadata);
				}

				break;

			}

		}

		if (retValue == null) {
			retValue = strategy.getDouble(attributeMetadata);
		}

		return retValue;

	}

	/**
	 * Returns either a customised long value if a {@link PodamLongValue}
	 * annotation was provided or a random long if this was not the case
	 *
	 * @param annotations
	 *            The list of annotations for the int attribute
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 *
	 * @return Either a customised long value if a {@link PodamLongValue}
	 *         annotation was provided or a random long if this was not the case
	 *
	 * @throws IllegalArgumentException
	 *             If it was not possible to convert
	 *             {@link PodamLongValue#numValue()} to a Long
	 */
	private Long getLongValueWithinRange(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) {

		Long retValue = null;

		for (Annotation annotation : annotations) {

			if (PodamLongValue.class.isAssignableFrom(annotation.getClass())) {
				PodamLongValue longStrategy = (PodamLongValue) annotation;

				String numValueStr = longStrategy.numValue();
				if (null != numValueStr && !"".equals(numValueStr)) {
					try {
						retValue = Long.valueOf(numValueStr);
					} catch (NumberFormatException nfe) {
						String errMsg = THE_ANNOTATION_VALUE_STR
								+ numValueStr
								+ " could not be converted to a Long. An exception will be thrown.";
						LOG.error(errMsg);
						throw new IllegalArgumentException(errMsg, nfe);
					}
				} else {

					long minValue = longStrategy.minValue();
					long maxValue = longStrategy.maxValue();

					// Sanity check
					if (minValue > maxValue) {
						maxValue = minValue;
					}

					retValue = strategy.getLongInRange(minValue, maxValue,
							attributeMetadata);

				}

				break;

			}

		}

		if (retValue == null) {
			retValue = strategy.getLong(attributeMetadata);
		}

		return retValue;
	}

	/**
	 * It attempts to resolve the given class as a wrapper class and if this is
	 * the case it assigns a random value
	 *
	 *
	 * @param candidateWrapperClass
	 *            The class which might be a wrapper class
	 * @param attributeMetadata
	 *            The attribute's metadata, if any, used for customisation
	 * @return {@code null} if this is not a wrapper class, otherwise an Object
	 *         with the value for the wrapper class
	 */
	private Object resolveWrapperValue(Class<?> candidateWrapperClass,
			List<Annotation> annotations, AttributeMetadata attributeMetadata) {

		Object retValue = null;

		if (candidateWrapperClass.equals(Integer.class)) {

			retValue = getIntegerValueWithinRange(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Long.class)) {

			retValue = getLongValueWithinRange(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Float.class)) {

			retValue = getFloatValueWithinRange(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Double.class)) {

			retValue = getDoubleValueWithinRange(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Boolean.class)) {

			retValue = getBooleanValueForAnnotation(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Byte.class)) {

			retValue = getByteValueWithinRange(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Short.class)) {

			retValue = getShortValueWithinRange(annotations, attributeMetadata);

		} else if (candidateWrapperClass.equals(Character.class)) {

			retValue = getCharacterValueWithinRange(annotations, attributeMetadata);

		} else {

			throw new IllegalArgumentException(
					String.format("%s is unsupported wrapper type",
							candidateWrapperClass));

		}

		return retValue;
	}

	/**
	 * It creates and returns an instance of the given class if at least one of
	 * its constructors has been annotated with {@link PodamConstructor}
	 *
	 * @param <T>
	 *            The type of the instance to return
	 *
	 * @param pojoClass
	 *            The class of which an instance is required
	 * @param pojos
	 *            How many instances of the same class have been created so far
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return an instance of the given class if at least one of its
	 *         constructors has been annotated with {@link PodamConstructor}
	 * @throws SecurityException
	 *             If an security was violated
	 */
	@SuppressWarnings({ UNCHECKED_STR })
	private <T> T instantiatePojo(Class<T> pojoClass,
			Map<Class<?>, Integer> pojos, Type... genericTypeArgs)
			throws SecurityException {

		T retValue = null;

		Constructor<?>[] constructors = pojoClass.getConstructors();
		if (constructors.length == 0 || Modifier.isAbstract(pojoClass.getModifiers())) {
			/* No public constructors, we will try static factory methods */
			try {
				retValue = (T) instantiatePojoWithoutConstructors(
						pojoClass, pojos, genericTypeArgs);
			} catch (Exception e) {
				LOG.debug("We couldn't create an instance for pojo: "
						+ pojoClass + " with factory methods, will "
						+ " try non-public constructors.", e);
			}

			/* Then non-public constructors */
			if (retValue == null) {
				constructors = pojoClass.getDeclaredConstructors();
			}
		}

		if (retValue == null && constructors.length > 0) {

			/* We want constructor with minumum number of parameters
			 * to speed up the creation */
			strategy.sort(constructors);

			for (Constructor<?> constructor : constructors) {

				try {
					Object[] parameterValues = getParameterValuesForConstructor(
							constructor, pojoClass, pojos, genericTypeArgs);

				// Being a generic method we cannot be sure on the identity of
				// T, therefore the mismatch between the newInstance() return
				// value (Object) and T is acceptable, thus the SuppressWarning
				// annotation

					// Security hack
					if (!constructor.isAccessible()) {
						constructor.setAccessible(true);
					}
					retValue = (T) constructor.newInstance(parameterValues);
					if (retValue != null) {
						LOG.debug("We could create an instance with constructor: "
								+ constructor);
						break;
					}
				} catch (Exception e) {
					LOG.debug("We couldn't create an instance for pojo: {} with"
							+ " constructor: {}. Will try with another one.",
							pojoClass, constructor, e);
				}
			}
		}

		if (retValue == null) {
			LOG.debug("For class {} PODAM could not possibly create"
					+ " a value. Will try other means.", pojoClass);
		}
		return retValue;
	}

	/**
	 * Generic method which returns an instance of the given class filled with
	 * values dictated by the strategy
	 *
	 * @param <T>
	 *            The type for which a filled instance is required
	 *
	 * @param pojoClass
	 *            The name of the class for which an instance filled with values
	 *            is required
	 * @param pojoMetadata
	 *            attribute metadata for POJOs produced recursively
	 * @param pojos
	 *            How many times {@code pojoClass} has been found. This will be
	 *            used for reentrant objects
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return An instance of &lt;T&gt; filled with dummy values
	 * @throws ClassNotFoundException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 *
	 * @throws PodamMockeryException
	 *             if a problem occurred while creating a POJO instance or while
	 *             setting its state
	 */
	@SuppressWarnings(UNCHECKED_STR)
	private <T> T manufacturePojoInternal(Class<T> pojoClass,
			AttributeMetadata pojoMetadata, Map<Class<?>, Integer> pojos,
			Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		LOG.debug("Manufacturing {} with parameters {}",
				pojoClass, Arrays.toString(genericTypeArgs));

		T retValue = null;

		// reuse object from memoization table
		T objectToReuse = (T) strategy.getMemoizedObject(pojoMetadata);
		if (objectToReuse != null) {
			return objectToReuse;
		}

		if (pojoClass.isEnum()) {

			int enumConstantsLength = pojoClass.getEnumConstants().length;
			if (enumConstantsLength > 0) {
				int enumIndex = strategy.getIntegerInRange(0,
						enumConstantsLength - 1,
						new AttributeMetadata(pojoClass, genericTypeArgs, pojoClass));
				return  pojoClass.getEnumConstants()[enumIndex];
			}
		}

		if (pojoClass.isPrimitive()) {
			// For JDK POJOs we can't retrieve attribute name
			return (T) resolvePrimitiveValue(pojoClass, Collections.<Annotation>emptyList(),
					new AttributeMetadata(pojoClass, genericTypeArgs, pojoClass));
		}

		if (pojoClass.isInterface()) {
			Class<T> specificClass = (Class<T>) strategy
					.getSpecificClass(pojoClass);
			if (!specificClass.equals(pojoClass)) {
				return this.manufacturePojoInternal(specificClass, pojoMetadata,
						pojos, genericTypeArgs);
			} else {
				LOG.info("{} is an interface. Resorting to {} external factory",
						pojoClass,
						externalFactory.getClass().getName());
				return externalFactory.manufacturePojo(pojoClass,
						genericTypeArgs);
			}
		}

		try {

			retValue = instantiatePojo(pojoClass, pojos, genericTypeArgs);
		} catch (SecurityException e) {
			throw new PodamMockeryException(
					"Security exception while applying introspection.", e);
		}

		if (retValue == null) {
			if (Modifier.isAbstract(pojoClass.getModifiers())) {
				Class<T> specificClass = (Class<T>) strategy
						.getSpecificClass(pojoClass);
				if (!specificClass.equals(pojoClass)) {
					return this.manufacturePojoInternal(specificClass, pojoMetadata,
							pojos, genericTypeArgs);
				}
			}
			LOG.info("Failed to manufacture {}. Resorting to {} external factory",
					pojoClass,
					externalFactory.getClass().getName());
			return externalFactory.manufacturePojo(pojoClass,
					genericTypeArgs);
		}

		// update memoization cache with new object
		// the reference is stored before properties are set so that recursive
		// properties can use it
		strategy.cacheMemoizedObject(pojoMetadata, retValue);

		if (retValue != null) {
			populatePojo(retValue, pojos, genericTypeArgs);
		}

		return retValue;
	}

	/**
	 * Fills given class filled with values dictated by the strategy
	 *
	 * @param pojo
	 *            An instance to be filled with dummy values
	 * @param pojos
	 *            How many times {@code pojoClass} has been found. This will be
	 *            used for reentrant objects
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return An instance of &lt;T&gt; filled with dummy values
	 * @throws ClassNotFoundException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	@SuppressWarnings(UNCHECKED_STR)
	private <T> T populatePojo(T pojo, Map<Class<?>, Integer> pojos,
			Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		Class<?> pojoClass = pojo.getClass();
		if (pojo instanceof Collection && ((Collection<?>)pojo).size() == 0) {
			fillCollection((Collection<? super Object>)pojo, pojos, genericTypeArgs);
		} else if (pojo instanceof Map && ((Map<?,?>)pojo).size() == 0) {
			fillMap((Map<? super Object,? super Object>)pojo, pojos, genericTypeArgs);
		}

		Class<?>[] parameterTypes = null;
		Class<?> attributeType = null;

		ClassInfo classInfo = classInfoStrategy.getClassInfo(pojo.getClass());

		final Map<String, Type> typeArgsMap = new HashMap<String, Type>();

		Set<ClassAttribute> classAttributes = classInfo.getClassAttributes();

		// According to JavaBeans standards, setters should have only
		// one argument
		Object setterArg = null;
		Iterator<ClassAttribute> iter = classAttributes.iterator();
		while (iter.hasNext()) {

			ClassAttribute attribute = iter.next();
			Set<Method> setters = attribute.getSetters();
			if (setters.isEmpty()) {
				if (attribute.getGetters().isEmpty()) {
					iter.remove();
				}
				continue;
			} else {
				iter.remove();
			}

			/* We want to find setter defined the latest */
			Method setter = null;
			for (Method current : setters) {
				if (setter == null || setter.getDeclaringClass().isAssignableFrom(current.getDeclaringClass())) {
					setter = current;
				}
			}

			parameterTypes = setter.getParameterTypes();
			if (parameterTypes.length != 1) {
				LOG.warn("Skipping setter with non-single arguments {}",
						setter);
				continue;
			}

			// A class which has got an attribute to itself (e.g.
			// recursive hierarchies)
			attributeType = parameterTypes[0];

			// If an attribute has been annotated with
			// PodamAttributeStrategy, it takes the precedence over any
			// other strategy. Additionally we don't pass the attribute
			// metadata for value customisation; if user went to the extent
			// of specifying a PodamAttributeStrategy annotation for an
			// attribute they are already customising the value assigned to
			// that attribute.

			String attributeName = PodamUtils
					.extractFieldNameFromSetterMethod(setter);
			List<Annotation> pojoAttributeAnnotations
					= PodamUtils.getFieldAnnotations(pojo.getClass(),
							attributeName);
			for (Annotation annotation : setter.getParameterAnnotations()[0]) {
				pojoAttributeAnnotations.add(annotation);
			}

			AttributeStrategy<?> attributeStrategy
					= findAttributeStrategy(pojoAttributeAnnotations, attributeType);
			if (null != attributeStrategy) {

				LOG.debug("The attribute: " + attributeName
						+ " will be filled using the following strategy: "
						+ attributeStrategy);

				setterArg = returnAttributeDataStrategyValue(attributeType,
						attributeStrategy);

			} else {

				if (typeArgsMap.isEmpty()) {
					Type[] genericTypeArgsExtra = fillTypeArgMap(typeArgsMap,
							pojoClass, genericTypeArgs);
					if (genericTypeArgsExtra != null) {
						LOG.warn("Lost generic type arguments {}",
								Arrays.toString(genericTypeArgsExtra));
					}
				}

				Type[] typeArguments = NO_TYPES;
				// If the parameter is a generic parameterized type resolve
				// the actual type arguments
				if (setter.getGenericParameterTypes()[0] instanceof ParameterizedType) {
					final ParameterizedType attributeParameterizedType = (ParameterizedType) setter
							.getGenericParameterTypes()[0];
					typeArguments = attributeParameterizedType
							.getActualTypeArguments();
				} else if (setter.getGenericParameterTypes()[0] instanceof TypeVariable) {
					final TypeVariable<?> typeVariable = (TypeVariable<?>) setter
							.getGenericParameterTypes()[0];
					Type type = typeArgsMap.get(typeVariable.getName());
					if (type instanceof ParameterizedType) {
						final ParameterizedType attributeParameterizedType = (ParameterizedType) type;

						typeArguments = attributeParameterizedType
								.getActualTypeArguments();
						attributeType = (Class<?>) attributeParameterizedType
								.getRawType();
					} else {
						attributeType = (Class<?>) type;
					}
				}
				AtomicReference<Type[]> typeGenericTypeArgs
						= new AtomicReference<Type[]>(NO_TYPES);
				for (int i = 0; i < typeArguments.length; i++) {
					if (typeArguments[i] instanceof TypeVariable) {
						Class<?> resolvedType = resolveGenericParameter(typeArguments[i],
								typeArgsMap, typeGenericTypeArgs);
						if (!Collection.class.isAssignableFrom(resolvedType) && !Map.class.isAssignableFrom(resolvedType)) {
							typeArguments[i] = resolvedType;
						}
					}
				}

				setterArg = manufactureAttributeValue(pojo, pojos,
						attributeType, setter.getGenericParameterTypes()[0],
						pojoAttributeAnnotations, attributeName,
						typeArgsMap, typeArguments);
			}

			if (setterArg != null) {
				try {
					setter.invoke(pojo, setterArg);
				} catch(IllegalAccessException e) {
					LOG.warn("{} is not accessible. Setting it to accessible."
							+ " However this is a security hack and your code"
							+ " should really adhere to JavaBeans standards.",
							setter.toString());
					setter.setAccessible(true);
					setter.invoke(pojo, setterArg);
				}
			} else {

				LOG.warn("Couldn't find a suitable value for attribute {}[{}]. Will delegate to {}",
						pojoClass, attributeType, externalFactory);
			}
		}

		for (ClassAttribute readOnlyAttribute : classAttributes) {

			Method getter = readOnlyAttribute.getGetters().iterator().next();
			if (getter != null && !getter.getReturnType().isPrimitive()) {

				if (getter.getGenericParameterTypes().length == 0) {

					Object fieldValue = null;
					try {
						fieldValue = getter.invoke(pojo, NO_ARGS);
					} catch(Exception e) {
						LOG.debug("Cannot access {}, skipping", getter);
					}
					if (fieldValue != null) {

						LOG.debug("Populating read-only field {}", getter);
						Type[] genericTypeArgsAll;
						if (getter.getGenericReturnType() instanceof ParameterizedType) {

							if (typeArgsMap.isEmpty()) {
								Type[] genericTypeArgsExtra = fillTypeArgMap(typeArgsMap,
										pojoClass, genericTypeArgs);
								if (genericTypeArgsExtra != null) {
									LOG.warn("Lost generic type arguments {}",
											Arrays.toString(genericTypeArgsExtra));
								}
							}

							ParameterizedType paramType
									= (ParameterizedType) getter.getGenericReturnType();
							Type[] actualTypes = paramType.getActualTypeArguments();
							genericTypeArgsAll = mergeActualAndSuppliedGenericTypes(
									actualTypes, genericTypeArgs,
									typeArgsMap);
						} else {

							genericTypeArgsAll = genericTypeArgs;
						}

						if (getter.getReturnType().getTypeParameters().length > genericTypeArgsAll.length) {

							Type[] missing = new Type[
									getter.getReturnType().getTypeParameters().length
									- genericTypeArgsAll.length];
							for (int i = 0; i < missing.length; i++) {
								missing[i] = Object.class;
							}
							LOG.warn("Missing type parameters, appended {}",
									Arrays.toString(missing));
							genericTypeArgsAll =
									mergeTypeArrays(genericTypeArgsAll, missing);
						}

						Class<?> fieldClass = fieldValue.getClass();
						Integer depth = pojos.get(fieldClass);
						if (depth == null) {
							depth = -1;
						}
						if (depth <= strategy.getMaxDepth(fieldClass)) {

							pojos.put(fieldClass, depth + 1);
							populatePojo(fieldValue, pojos, genericTypeArgsAll);
							pojos.put(fieldClass, depth);

						} else {

							LOG.warn("Loop in filling read-only field {} detected.",
									getter);
						}
					}
				} else {

					LOG.warn("Skipping invalid getter {}", getter);
				}
			}
		}

		// It executes any extra methods
		Collection<Method> extraMethods = classInfoStrategy.getExtraMethods(pojoClass);
		if (null != extraMethods) {
			for (Method extraMethod : extraMethods) {

				Class<?>[] argTypes = extraMethod.getParameterTypes();
				Type[] genericTypes = extraMethod.getGenericParameterTypes();
				Annotation[][] annotations = extraMethod.getParameterAnnotations();

				Object[] args = new Object[argTypes.length];
				for (int i = 0; i < argTypes.length; i++) {

					List<Annotation> paramAnnotations = Arrays.asList(annotations[i]);
					args[i] = manufactureParameterValue(pojos, argTypes[i],
							genericTypes[i], paramAnnotations, typeArgsMap,
							genericTypeArgs);
				}
				extraMethod.invoke(pojo, args);
			}
		}

		return pojo;
	}

	/**
	 * It manufactures and returns the value for a POJO method parameter.
	 *
	 *
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param parameterType
	 *            The type of the attribute for which a value is being
	 *            manufactured
	 * @param genericType
	 *            The generic type of the attribute for which a value is being
	 *            manufactured
	 * @param annotations
	 *            The annotations for the attribute being considered
	 * @param typeArgsMap
	 *            a map relating the generic class arguments ("&lt;T, V&gt;" for
	 *            example) with their actual types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return The value for a parameter
	 *
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 * @throws IllegalArgumentException
	 *             <ul>
	 *             <li>If an illegal argument was passed</li>
	 *             <li>If an invalid value was set for a precise value in an
	 *             annotation and such value could not be converted to the
	 *             desired type</li>
	 *             </ul>
	 *
	 */
	private Object manufactureParameterValue(Map<Class<?>, Integer> pojos,
			Class<?> parameterType, Type genericType,
			List<Annotation> annotations, Map<String, Type> typeArgsMap,
			Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		String attributeName = null;

		return manufactureAttributeValue(OBJECT, pojos, parameterType,
				genericType, annotations, attributeName, typeArgsMap,
				genericTypeArgs);
	}

	/**
	 * It manufactures and returns the value for a POJO attribute.
	 *
	 *
	 * @param pojo
	 *            The POJO being filled with values
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param attributeType
	 *            The type of the attribute for which a value is being
	 *            manufactured
	 * @param genericAttributeType
	 *            The generic type of the attribute for which a value is being
	 *            manufactured
	 * @param annotations
	 *            The annotations for the attribute being considered
	 * @param attributeName
	 *            The attribute name
	 * @param typeArgsMap
	 *            a map relating the generic class arguments ("&lt;T, V&gt;" for
	 *            example) with their actual types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return The value for an attribute
	 *
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 * @throws IllegalArgumentException
	 *             <ul>
	 *             <li>If an illegal argument was passed</li>
	 *             <li>If an invalid value was set for a precise value in an
	 *             annotation and such value could not be converted to the
	 *             desired type</li>
	 *             </ul>
	 *
	 */
	private Object manufactureAttributeValue(Object pojo,
			Map<Class<?>, Integer> pojos, Class<?> attributeType,
			Type genericAttributeType, List<Annotation> annotations,
			String attributeName, Map<String, Type> typeArgsMap,
			Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {
		Object attributeValue = null;

		Class<?> realAttributeType;
		if (Object.class.equals(attributeType) && attributeType != genericAttributeType) {
			AtomicReference<Type[]> elementGenericTypeArgs
					= new AtomicReference<Type[]>(NO_TYPES);
			realAttributeType = resolveGenericParameter(genericAttributeType,
					typeArgsMap, elementGenericTypeArgs);
		} else {
			realAttributeType = attributeType;
		}
		AttributeMetadata attributeMetadata = new AttributeMetadata(
				attributeName, realAttributeType, genericTypeArgs, annotations,
				pojo.getClass());

		// Primitive type
		if (realAttributeType.isPrimitive()) {

			attributeValue = resolvePrimitiveValue(realAttributeType,
					annotations, attributeMetadata);

			// Wrapper type
		} else if (isWrapper(realAttributeType)) {

			attributeValue = resolveWrapperValue(realAttributeType,
					annotations, attributeMetadata);

			// String type
		} else if (realAttributeType.equals(String.class)) {

			attributeValue = resolveStringValue(annotations, attributeMetadata);

		} else if (realAttributeType.isArray()) {

			// Array type

			attributeValue = resolveArrayElementValue(realAttributeType,
					genericAttributeType, attributeName, pojos, annotations, pojo,
					typeArgsMap);

			// Otherwise it's a different type of Object (including
			// the Object class)
		} else if (Collection.class.isAssignableFrom(realAttributeType)) {

			attributeValue = resolveCollectionValueWhenCollectionIsPojoAttribute(
					pojo, pojos, realAttributeType, attributeName,
					annotations, typeArgsMap, genericTypeArgs);

		} else if (Map.class.isAssignableFrom(realAttributeType)) {

			attributeValue = resolveMapValueWhenMapIsPojoAttribute(pojo,
					pojos, realAttributeType, attributeName, annotations,
					typeArgsMap, genericTypeArgs);

		} else if (realAttributeType.isEnum()) {

			// Enum type
			int enumConstantsLength = realAttributeType.getEnumConstants().length;

			if (enumConstantsLength > 0) {
				int enumIndex = strategy.getIntegerInRange(0,
						enumConstantsLength, attributeMetadata)
						% enumConstantsLength;
				attributeValue = realAttributeType.getEnumConstants()[enumIndex];
			}

		} else if (Type.class.isAssignableFrom(realAttributeType)) {

			Type paremeterType = null;
			if (genericAttributeType instanceof ParameterizedType) {
				ParameterizedType parametrized =  (ParameterizedType) genericAttributeType;
				Type[] arguments = parametrized.getActualTypeArguments();
				if (arguments.length > 0) {
					paremeterType = arguments[0];
				}
			} else if (realAttributeType.getTypeParameters().length > 0) {
				paremeterType = realAttributeType.getTypeParameters()[0];
			}

			if (paremeterType != null) {
				AtomicReference<Type[]> elementGenericTypeArgs
						= new AtomicReference<Type[]>(NO_TYPES);
				attributeValue = resolveGenericParameter(paremeterType,
						typeArgsMap, elementGenericTypeArgs);
			} else {
				LOG.error("{} is missing generic type argument, supplied {} {}",
						genericAttributeType, typeArgsMap,
						Arrays.toString(genericTypeArgs));
			}

		}

		// For any other type, we use the PODAM strategy
		if (attributeValue == null) {

			TypeVariable<?>[] typeParams = attributeType.getTypeParameters();
			Type[] genericTypeArgsAll = mergeActualAndSuppliedGenericTypes(
					typeParams, genericTypeArgs, typeArgsMap);

			Integer depth = pojos.get(realAttributeType);
			if (depth == null) {
				depth = -1;
			}
			if (depth <= strategy.getMaxDepth(pojo.getClass())) {

				pojos.put(realAttributeType, depth + 1);

				attributeValue = this.manufacturePojoInternal(
						realAttributeType, attributeMetadata, pojos, genericTypeArgsAll);
				pojos.put(realAttributeType, depth);

			} else {

				LOG.warn("Loop in {} production detected. Resorting to {} external factory",
						realAttributeType, externalFactory.getClass().getName());
				attributeValue = externalFactory.manufacturePojo(
						realAttributeType, genericTypeArgsAll);

			}
		}

		return attributeValue;
	}

	/**
	 * Utility to merge actual types with supplied array of generic type
	 * substitutions
	 *
	 * @param actualTypes
	 *            an array of types used for field or POJO declaration 
	 * @param suppliedTypes
	 *            an array of supplied types for generic type substitution 
	 * @param typeArgsMap
	 *            a map relating the generic class arguments ("&lt;T, V&gt;" for
	 *            example) with their actual types
	 * @return An array of merged actual and supplied types with generic types
	 *            resolved
	 */
	private Type[] mergeActualAndSuppliedGenericTypes(
			Type[] actualTypes, Type[] suppliedTypes,
			Map<String, Type> typeArgsMap) {

		List<Type> resolvedTypes = new ArrayList<Type>();
		List<Type> substitutionTypes = new ArrayList<Type>(Arrays.asList(suppliedTypes));
		for (int i = 0; i < actualTypes.length; i++) {

			Type type = null;
			if (actualTypes[i] instanceof TypeVariable) {
				type = typeArgsMap.get(((TypeVariable<?>)actualTypes[i]).getName());
			} else if (actualTypes[i] instanceof Class) {
				type = actualTypes[i];
			} else if (actualTypes[i] instanceof WildcardType) {
				AtomicReference<Type[]> methodGenericTypeArgs
						= new AtomicReference<Type[]>(NO_TYPES);
				type = resolveGenericParameter(actualTypes[i], typeArgsMap,
						methodGenericTypeArgs);
			}
			if (type != null) {
				resolvedTypes.add(type);
				if (!substitutionTypes.isEmpty() && substitutionTypes.get(0).equals(type)) {
					substitutionTypes.remove(0);
				}
			}
		}
		Type[] resolved = resolvedTypes.toArray(new Type[resolvedTypes.size()]);
		Type[] supplied = substitutionTypes.toArray(new Type[substitutionTypes.size()]);
		return mergeTypeArrays(resolved, supplied);
	}

	/**
	 * It creates and returns a String value, eventually customised by
	 * annotations
	 *
	 * @param annotations
	 *            The list of annotations used to customise the String value, if
	 *            any
	 * @param attributeMetadata
	 * @return a String value, eventually customised by annotations
	 * @throws IllegalAccessException
	 *             If an exception occurred while creating an instance of the
	 *             strategy
	 * @throws InstantiationException
	 *             If an exception occurred while creating an instance of the
	 *             strategy
	 *
	 * @throws IllegalArgumentException
	 *             If {@link PodamStrategyValue} was specified but the type was
	 *             not correct for the attribute being set
	 */
	private String resolveStringValue(List<Annotation> annotations,
			AttributeMetadata attributeMetadata) throws InstantiationException,
			IllegalAccessException {

		String retValue = null;

		if (annotations == null || annotations.isEmpty()) {

			retValue = strategy.getStringValue(attributeMetadata);

		} else {

			for (Annotation annotation : annotations) {

				if (!PodamStringValue.class.isAssignableFrom(annotation
						.getClass())) {
					continue;
				}

				// A specific value takes precedence over the length
				PodamStringValue podamAnnotation = (PodamStringValue) annotation;

				if (podamAnnotation.strValue() != null
						&& podamAnnotation.strValue().length() > 0) {

					retValue = podamAnnotation.strValue();

				} else {

					retValue = strategy.getStringOfLength(
							podamAnnotation.length(), attributeMetadata);

				}

			}

			if (retValue == null) {
				retValue = strategy.getStringValue(attributeMetadata);
			}

		}

		return retValue;
	}

	/**
	 * It returns a {@link AttributeStrategy} if one was specified in
	 * annotations, or {@code null} otherwise.
	 *
	 * @param annotations
	 *            The list of annotations
	 * @param attributeType
	 *            Type of attribute expected to be returned
	 * @return {@link AttributeStrategy}, if {@link PodamStrategyValue} or bean
	 *         validation constraint annotation was found among annotations
	 * @throws IllegalAccessException
	 *         if attribute strategy cannot be instantiated
	 * @throws InstantiationException 
	 *         if attribute strategy cannot be instantiated
	 */
	private AttributeStrategy<?> findAttributeStrategy(
			List<Annotation> annotations, Class<?> attributeType)
					throws InstantiationException, IllegalAccessException {

		AttributeStrategy<?> retValue = null;

		for (Annotation annotation : annotations) {
			if (annotation instanceof PodamStrategyValue) {
				PodamStrategyValue strategyAnnotation = (PodamStrategyValue) annotation;
				return strategyAnnotation.value().newInstance();
			}
		}

		return retValue;
	}

	/**
	 * It returns {@code true} if this class is a wrapper class, {@code false}
	 * otherwise
	 *
	 * @param candidateWrapperClass
	 *            The class to check
	 * @return {@code true} if this class is a wrapper class, {@code false}
	 *         otherwise
	 */
	private boolean isWrapper(Class<?> candidateWrapperClass) {

		return candidateWrapperClass.equals(Byte.class) ? true
				: candidateWrapperClass.equals(Boolean.class) ? true
						: candidateWrapperClass.equals(Character.class) ? true
								: candidateWrapperClass.equals(Short.class) ? true
										: candidateWrapperClass
												.equals(Integer.class) ? true
												: candidateWrapperClass
														.equals(Long.class) ? true
														: candidateWrapperClass
																.equals(Float.class) ? true
																: candidateWrapperClass
																		.equals(Double.class) ? true
																		: false;
	}

	/**
	 * It returns a collection of some sort with some data in it.
	 *
	 *
	 * @param collectionType
	 *            The POJO being analyzed
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param collectionType
	 *            The type of the attribute being evaluated
	 * @param annotations
	 *            The set of annotations for the annotated attribute. It might
	 *            be empty
	 * @param attributeName
	 *            The name of the field being set
	 * @param typeArgsMap
	 *            a map relating the generic class arguments ("&lt;T, V&gt;" for
	 *            example) with their actual types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return a collection of some sort with some data in it
	 * @throws PodamMockeryException
	 *             An exception occurred while resolving the collection
	 * @throws IllegalArgumentException
	 *             If the field name is null or empty
	 */
	private Collection<? super Object> resolveCollectionValueWhenCollectionIsPojoAttribute(
			Object pojo, Map<Class<?>, Integer> pojos,
			Class<?> collectionType, String attributeName,
			List<Annotation> annotations, Map<String, Type> typeArgsMap,
			Type... genericTypeArgs) {

		// This needs to be generic because collections can be of any type
		Collection<? super Object> retValue = null;
		if (null != pojo && null != attributeName) {

			retValue = PodamUtils.getFieldValue(pojo, attributeName);
		}

		retValue = resolveCollectionType(collectionType, retValue);

		if (null == retValue) {
			return null;
		}

		try {

			Class<?> typeClass = null;

			AtomicReference<Type[]> elementGenericTypeArgs = new AtomicReference<Type[]>(
					NO_TYPES);
			if (genericTypeArgs == null || genericTypeArgs.length == 0) {

				LOG.warn("The collection attribute: "
						+ attributeName
						+ " does not have a type. We will assume Object for you");
				// Support for non-generified collections
				typeClass = Object.class;

			} else {
				Type actualTypeArgument = genericTypeArgs[0];

				typeClass = resolveGenericParameter(actualTypeArgument,
						typeArgsMap, elementGenericTypeArgs);
			}

			fillCollection(pojos, annotations, attributeName, retValue, typeClass,
					elementGenericTypeArgs.get());

		} catch (SecurityException e) {
			throw new PodamMockeryException(RESOLVING_COLLECTION_EXCEPTION_STR,
					e);
		} catch (IllegalArgumentException e) {
			throw new PodamMockeryException(RESOLVING_COLLECTION_EXCEPTION_STR,
					e);
		} catch (InstantiationException e) {
			throw new PodamMockeryException(RESOLVING_COLLECTION_EXCEPTION_STR,
					e);
		} catch (IllegalAccessException e) {
			throw new PodamMockeryException(RESOLVING_COLLECTION_EXCEPTION_STR,
					e);
		} catch (ClassNotFoundException e) {
			throw new PodamMockeryException(RESOLVING_COLLECTION_EXCEPTION_STR,
					e);
		} catch (InvocationTargetException e) {
			throw new PodamMockeryException(RESOLVING_COLLECTION_EXCEPTION_STR,
					e);
		}

		return retValue;
	}

	/**
	 * It fills a collection with the required number of elements of the
	 * required type.
	 *
	 * <p>
	 * This method has a so-called side effect. It updates the collection passed
	 * as argument.
	 * </p>
	 *
	 * @param collection
	 *          The Collection to be filled
	 * @param pojos
	 *          Set of manufactured pojos' types
	 * @param genericTypeArgs
	 *          The generic type arguments for the current generic class
	 *          instance
	 * @throws InstantiationException
	 *          If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *          If security was violated while creating the object
	 * @throws InvocationTargetException
	 *          If an exception occurred while invoking the constructor or
	 *          factory method
	 * @throws ClassNotFoundException
	 *          If it was not possible to create a class from a string
	 *
	 */
	private void fillCollection(Collection<? super Object> collection,
			Map<Class<?>, Integer> pojos, Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		final Map<String, Type> typeArgsMap = new HashMap<String, Type>();
		Class<?> pojoClass = collection.getClass();
		Type[] genericTypeArgsExtra = fillTypeArgMap(typeArgsMap,
				pojoClass, genericTypeArgs);

		Annotation[] annotations = collection.getClass().getAnnotations();
		AtomicReference<Type[]> elementGenericTypeArgs = new AtomicReference<Type[]>(
				NO_TYPES);
		Class<?> collectionClass = pojoClass;
		Type[] typeParams = collectionClass.getTypeParameters();
		main : while (typeParams.length < 1) {
			for (Type genericIface : collectionClass.getGenericInterfaces()) {
				Class<?> clazz = resolveGenericParameter(genericIface,
						typeArgsMap, elementGenericTypeArgs);
				if (Collection.class.isAssignableFrom(clazz)) {
					collectionClass = clazz;
					typeParams = elementGenericTypeArgs.get();
					continue main;
				}
			}
			Type type = collectionClass.getGenericSuperclass();
			if (type != null) {
				Class<?> clazz = resolveGenericParameter(type, typeArgsMap,
						elementGenericTypeArgs);
				if (Collection.class.isAssignableFrom(clazz)) {
					collectionClass = clazz;
					typeParams = elementGenericTypeArgs.get();
					continue main;
				}
			}
			if (Collection.class.equals(collectionClass)) {
				LOG.warn("Collection {} doesn't have generic types,"
						+ "will use Object instead", pojoClass);
				typeParams = new Type[] { Object.class };
			}
		}
		Class<?> elementTypeClass = resolveGenericParameter(typeParams[0],
					typeArgsMap, elementGenericTypeArgs);
		Type[] elementGenericArgs = mergeTypeArrays(elementGenericTypeArgs.get(),
				genericTypeArgsExtra);
		String attributeName = null;
		fillCollection(pojos, Arrays.asList(annotations), attributeName,
				collection, elementTypeClass, elementGenericArgs);
	}

	/**
	 * It fills a collection with the required number of elements of the
	 * required type.
	 *
	 * <p>
	 * This method has a so-called side effect. It updates the collection passed
	 * as argument.
	 * </p>
	 *
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param annotations
	 *            The annotations for this attribute
	 * @param attributeName
	 *            The attribute name of collection in enclosing POJO class 
	 * @param collection
	 *            The Collection to be filled
	 * @param collectionElementType
	 *            The type of the collection element
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 *
	 */
	private void fillCollection(Map<Class<?>, Integer> pojos,
			List<Annotation> annotations, String attributeName,
			Collection<? super Object> collection,
			Class<?> collectionElementType, Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		// If the user defined a strategy to fill the collection elements,
		// we use it
		PodamCollection collectionAnnotation = null;
		AttributeStrategy<?> elementStrategy = null;
		for (Annotation annotation : annotations) {
			if (PodamCollection.class.isAssignableFrom(annotation.getClass())) {
				collectionAnnotation = (PodamCollection) annotation;
				break;
			}

		}

		int nbrElements;

		if (null != collectionAnnotation) {

			nbrElements = collectionAnnotation.nbrElements();
			elementStrategy = collectionAnnotation.collectionElementStrategy()
					.newInstance();
		} else {

			nbrElements = strategy
						.getNumberOfCollectionElements(collectionElementType);
		}

		try {
			if (collection.size() > nbrElements) {

				collection.clear();
			}

			for (int i = collection.size(); i < nbrElements; i++) {

				// The default
				Object element;
				if (null != elementStrategy
						&& ObjectStrategy.class.isAssignableFrom(elementStrategy
								.getClass())
								&& Object.class.equals(collectionElementType)) {
					LOG.debug("Element strategy is ObjectStrategy and collection element is of type Object: using the ObjectStrategy strategy");
					element = elementStrategy.getValue();
				} else if (null != elementStrategy
						&& !ObjectStrategy.class.isAssignableFrom(elementStrategy
								.getClass())) {
					LOG.debug("Collection elements will be filled using the following strategy: "
							+ elementStrategy);
					element = returnAttributeDataStrategyValue(
							collectionElementType, elementStrategy);
				} else {
					Map<String, Type> nullTypeArgsMap = new HashMap<String, Type>();
					element = manufactureAttributeValue(collection, pojos,
							collectionElementType, collectionElementType,
							annotations, attributeName, nullTypeArgsMap, genericTypeArgs);
				}
				collection.add(element);
			}
		} catch (UnsupportedOperationException e) {

			LOG.warn("Cannot fill immutable collection {}", collection.getClass());
		}
	}

	/**
	 * It manufactures and returns a Map with at least one element in it
	 *
	 * @param pojo
	 *            The POJO being initialized
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param attributeType
	 *            The type of the POJO map attribute
	 * @param attributeName
	 *            The POJO attribute name
	 * @param annotations
	 *            The annotations specified for this attribute
	 * @param typeArgsMap
	 *            a map relating the generic class arguments ("&lt;T, V&gt;" for
	 *            example) with their actual types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 * @return Map with at least one element in it
	 *
	 * @throws IllegalArgumentException
	 *             <ul>
	 *             <li>If the attribute name is null or empty</li>
	 *             <li>If the array of types of the Map has length different
	 *             from 2</li>
	 *             </ul>
	 *
	 * @throws PodamMockeryException
	 *             If an error occurred while creating the Map object
	 */
	private Map<? super Object, ? super Object> resolveMapValueWhenMapIsPojoAttribute(
			Object pojo, Map<Class<?>, Integer> pojos,
			Class<?> attributeType, String attributeName,
			List<Annotation> annotations, Map<String, Type> typeArgsMap,
			Type... genericTypeArgs) {

		Map<? super Object, ? super Object> retValue = null;
		if (null != pojo && null != attributeName) {

			retValue = PodamUtils.getFieldValue(pojo, attributeName);
		}

		retValue = resolveMapType(attributeType, retValue);

		if (null == retValue) {
			return null;
		}

		try {

			Class<?> keyClass = null;

			Class<?> elementClass = null;

			AtomicReference<Type[]> keyGenericTypeArgs = new AtomicReference<Type[]>(
					NO_TYPES);
			AtomicReference<Type[]> elementGenericTypeArgs = new AtomicReference<Type[]>(
					NO_TYPES);
			if (genericTypeArgs == null || genericTypeArgs.length == 0) {

				LOG.warn("Map attribute: "
						+ attributeName
						+ " is non-generic. We will assume a Map<Object, Object> for you.");

				keyClass = Object.class;

				elementClass = Object.class;

			} else {

				// Expected only key, value type
				if (genericTypeArgs.length != 2) {
					throw new IllegalStateException(
							"In a Map only key value generic type are expected.");
				}

				Type[] actualTypeArguments = genericTypeArgs;
				keyClass = resolveGenericParameter(actualTypeArguments[0],
						typeArgsMap, keyGenericTypeArgs);
				elementClass = resolveGenericParameter(actualTypeArguments[1],
						typeArgsMap, elementGenericTypeArgs);
			}

			MapArguments mapArguments = new MapArguments();
			mapArguments.setAttributeName(attributeName);
			mapArguments.setPojos(pojos);
			mapArguments.setAnnotations(annotations);
			mapArguments.setMapToBeFilled(retValue);
			mapArguments.setKeyClass(keyClass);
			mapArguments.setElementClass(elementClass);
			mapArguments.setKeyGenericTypeArgs(keyGenericTypeArgs.get());
			mapArguments
					.setElementGenericTypeArgs(elementGenericTypeArgs.get());

			fillMap(mapArguments);

		} catch (InstantiationException e) {
			throw new PodamMockeryException(MAP_CREATION_EXCEPTION_STR, e);
		} catch (IllegalAccessException e) {
			throw new PodamMockeryException(MAP_CREATION_EXCEPTION_STR, e);
		} catch (SecurityException e) {
			throw new PodamMockeryException(MAP_CREATION_EXCEPTION_STR, e);
		} catch (ClassNotFoundException e) {
			throw new PodamMockeryException(MAP_CREATION_EXCEPTION_STR, e);
		} catch (InvocationTargetException e) {
			throw new PodamMockeryException(MAP_CREATION_EXCEPTION_STR, e);
		}

		return retValue;
	}

	/**
	 * It fills a Map with the required number of elements of the required type.
	 *
	 * <p>
	 * This method has a so-called side-effect. It updates the Map given as
	 * argument.
	 * </p>
	 *
	 * @param map
	 *          The map being initialised
	 * @param pojos
	 *          Set of manufactured pojos' types
	 * @param genericTypeArgs
	 *          The generic type arguments for the current generic class
	 *          instance
	 * @throws InstantiationException
	 *          If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *          If security was violated while creating the object
	 * @throws InvocationTargetException
	 *          If an exception occurred while invoking the constructor or
	 *          factory method
	 * @throws ClassNotFoundException
	 *          If it was not possible to create a class from a string
	 *
	 */
	private void fillMap(Map<? super Object, ? super Object> map,
			Map<Class<?>, Integer> pojos, Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		final Map<String, Type> typeArgsMap = new HashMap<String, Type>();
		Class<?> pojoClass = map.getClass();
		Type[] genericTypeArgsExtra = fillTypeArgMap(typeArgsMap,
				pojoClass, genericTypeArgs);

		Class<?> mapClass = pojoClass;
		AtomicReference<Type[]> elementGenericTypeArgs = new AtomicReference<Type[]>(
				NO_TYPES);
		Type[] typeParams = mapClass.getTypeParameters();
		main : while (typeParams.length < 2) {
			for (Type genericIface : mapClass.getGenericInterfaces()) {
				Class<?> clazz = resolveGenericParameter(genericIface, typeArgsMap, elementGenericTypeArgs);
				if (Map.class.isAssignableFrom(clazz)) {
					typeParams = elementGenericTypeArgs.get();
					mapClass = clazz;
					continue main;
				}
			}
			Type type = mapClass.getGenericSuperclass();
			if (type != null) {
				Class<?> clazz = resolveGenericParameter(type, typeArgsMap, elementGenericTypeArgs);
				if (Map.class.isAssignableFrom(clazz)) {
					typeParams = elementGenericTypeArgs.get();
					mapClass = clazz;
					continue main;
				}
			}
			if (Map.class.equals(mapClass)) {
				LOG.warn("Map {} doesn't have generic types,"
						+ "will use Object, Object instead", pojoClass);
				typeParams = new Type[] { Object.class, Object.class };
			}
		}
		AtomicReference<Type[]> keyGenericTypeArgs = new AtomicReference<Type[]>(
				NO_TYPES);
		Class<?> keyClass = resolveGenericParameter(typeParams[0],
					typeArgsMap, keyGenericTypeArgs);
		Class<?> elementClass = resolveGenericParameter(typeParams[1],
					typeArgsMap, elementGenericTypeArgs);

		Type[] keyGenericArgs = mergeTypeArrays(keyGenericTypeArgs.get(),
				genericTypeArgsExtra);
		Type[] elementGenericArgs = mergeTypeArrays(elementGenericTypeArgs.get(),
				genericTypeArgsExtra);

		MapArguments mapArguments = new MapArguments();
		mapArguments.setPojos(pojos);
		mapArguments.setAnnotations(Arrays.asList(pojoClass.getAnnotations()));
		mapArguments.setMapToBeFilled(map);
		mapArguments.setKeyClass(keyClass);
		mapArguments.setElementClass(elementClass);
		mapArguments.setKeyGenericTypeArgs(keyGenericArgs);
		mapArguments.setElementGenericTypeArgs(elementGenericArgs);

		fillMap(mapArguments);
	}

	/**
	 * It fills a Map with the required number of elements of the required type.
	 *
	 * <p>
	 * This method has a so-called side-effect. It updates the Map given as
	 * argument.
	 * </p>
	 *
	 * @param mapArguments
	 *            The arguments POJO
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 *
	 */
	private void fillMap(MapArguments mapArguments)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		// If the user defined a strategy to fill the collection elements,
		// we use it
		PodamCollection collectionAnnotation = null;
		AttributeStrategy<?> keyStrategy = null;
		AttributeStrategy<?> elementStrategy = null;
		for (Annotation annotation : mapArguments.getAnnotations()) {
			if (PodamCollection.class.isAssignableFrom(annotation.getClass())) {
				collectionAnnotation = (PodamCollection) annotation;
				break;
			}
		}

		int nbrElements;

		if (null != collectionAnnotation) {

			nbrElements = collectionAnnotation.nbrElements();
			keyStrategy = collectionAnnotation.mapKeyStrategy().newInstance();
			elementStrategy = collectionAnnotation.mapElementStrategy()
					.newInstance();
		} else {

			nbrElements = strategy.getNumberOfCollectionElements(mapArguments
					.getElementClass());
		}

		Map<? super Object, ? super Object> map = mapArguments.getMapToBeFilled();
		try {
			if (map.size() > nbrElements) {

				map.clear();
			}

			for (int i = map.size(); i < nbrElements; i++) {

				Object keyValue = null;

				Object elementValue = null;

				MapKeyOrElementsArguments valueArguments = new MapKeyOrElementsArguments();
				valueArguments.setAttributeName(mapArguments.getAttributeName());
				valueArguments.setMapToBeFilled(mapArguments.getMapToBeFilled());
				valueArguments.setPojos(mapArguments.getPojos());
				valueArguments.setAnnotations(mapArguments.getAnnotations());
				valueArguments.setKeyOrValueType(mapArguments.getKeyClass());
				valueArguments.setElementStrategy(keyStrategy);
				valueArguments.setGenericTypeArgs(mapArguments
						.getKeyGenericTypeArgs());

				keyValue = getMapKeyOrElementValue(valueArguments);

				valueArguments.setKeyOrValueType(mapArguments.getElementClass());
				valueArguments.setElementStrategy(elementStrategy);
				valueArguments.setGenericTypeArgs(mapArguments
						.getElementGenericTypeArgs());

				elementValue = getMapKeyOrElementValue(valueArguments);

				/* ConcurrentHashMap doesn't allow null values */
				if (elementValue != null || !(map instanceof ConcurrentHashMap)) {
					map.put(keyValue, elementValue);
				}
			}
		} catch (UnsupportedOperationException e) {

			LOG.warn("Cannot fill immutable map {}", map.getClass());
		}
	}

	/**
	 * It fills a Map key or value with the appropriate value, considering
	 * attribute-level customisation.
	 *
	 * @param keyOrElementsArguments
	 *            The arguments POJO
	 * @return A Map key or value
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws IllegalArgumentException
	 *             <ul>
	 *             <li>If an illegal argument was passed</li>
	 *             <li>If an invalid value was set for a precise value in an
	 *             annotation and such value could not be converted to the
	 *             desired type</li>
	 *             </ul>
	 */
	private Object getMapKeyOrElementValue(
			MapKeyOrElementsArguments keyOrElementsArguments)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		Object retValue = null;

		if (null != keyOrElementsArguments.getElementStrategy()
				&& ObjectStrategy.class.isAssignableFrom(keyOrElementsArguments
						.getElementStrategy().getClass())
				&& Object.class.equals(keyOrElementsArguments
						.getKeyOrValueType())) {
			LOG.debug("Element strategy is ObjectStrategy and Map key or value type is of type Object: using the ObjectStrategy strategy");
			retValue = keyOrElementsArguments.getElementStrategy().getValue();
		} else if (null != keyOrElementsArguments.getElementStrategy()
				&& !ObjectStrategy.class
						.isAssignableFrom(keyOrElementsArguments
								.getElementStrategy().getClass())) {
			LOG.debug("Map key or value will be filled using the following strategy: "
					+ keyOrElementsArguments.getElementStrategy());
			retValue = returnAttributeDataStrategyValue(
					keyOrElementsArguments.getKeyOrValueType(),
					keyOrElementsArguments.getElementStrategy());

		} else {

			Map<String, Type> nullTypeArgsMap = new HashMap<String, Type>();
			retValue = manufactureAttributeValue(
					keyOrElementsArguments.getMapToBeFilled(),
					keyOrElementsArguments.getPojos(),
					keyOrElementsArguments.getKeyOrValueType(),
					keyOrElementsArguments.getKeyOrValueType(),
					keyOrElementsArguments.getAnnotations(),
					keyOrElementsArguments.getAttributeName(),
					nullTypeArgsMap,
					keyOrElementsArguments.getGenericTypeArgs());
		}
		return retValue;
	}

	/**
	 * It returns an Array with the first element set
	 *
	 *
	 * @param attributeType
	 *            The array type
	 * @param genericType
	 *            The array generic type
	 * @param attributeName
	 *            The array attribute name in enclosing POJO class
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param annotations
	 *            The annotations to be considered
	 * @param pojo
	 *            POJO containing attribute
	 * @param typeArgsMap
	 *            a map relating the generic class arguments ("&lt;T, V&gt;" for
	 *            example) with their actual types
	 * @return Array with the first element set
	 * @throws IllegalArgumentException
	 *             If an illegal argument was passed to the constructor
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 */
	private Object resolveArrayElementValue(Class<?> attributeType,
			Type genericType, String attributeName, Map<Class<?>, Integer> pojos,
			List<Annotation> annotations, Object pojo,
			Map<String, Type> typeArgsMap) throws InstantiationException,
			IllegalAccessException, InvocationTargetException,
			ClassNotFoundException {

		Class<?> componentType = null;
		Type genericComponentType = null;
		AtomicReference<Type[]> genericTypeArgs = new AtomicReference<Type[]>(
				NO_TYPES);
		if (genericType instanceof GenericArrayType) {
			genericComponentType = ((GenericArrayType) genericType).getGenericComponentType();
			if (genericComponentType instanceof TypeVariable) {
				TypeVariable<?> componentTypeVariable
						= (TypeVariable<?>) genericComponentType;
				final Type resolvedType
						= typeArgsMap.get(componentTypeVariable.getName());
				componentType
						= resolveGenericParameter(resolvedType, typeArgsMap,
								genericTypeArgs);
			}
		} else if (genericType instanceof Class) {
			Class<?> arrayClass = (Class<?>) genericType;
			genericComponentType = arrayClass.getComponentType();
		} else {
			genericComponentType = attributeType.getComponentType();
		}

		if (componentType == null) {
			componentType = attributeType.getComponentType();
		}

		// If the user defined a strategy to fill the collection elements,
		// we use it
		PodamCollection collectionAnnotation = null;
		AttributeStrategy<?> elementStrategy = null;
		for (Annotation annotation : annotations) {
			if (PodamCollection.class.isAssignableFrom(annotation.getClass())) {
				collectionAnnotation = (PodamCollection) annotation;
				break;
			}

		}

		int nbrElements;
		if (null != collectionAnnotation) {

			nbrElements = collectionAnnotation.nbrElements();
			elementStrategy = collectionAnnotation.collectionElementStrategy()
					.newInstance();
		} else {

			nbrElements = strategy.getNumberOfCollectionElements(attributeType);
		}

		Object arrayElement = null;
		Object array = Array.newInstance(componentType, nbrElements);

		for (int i = 0; i < nbrElements; i++) {

			// The default
			if (null != elementStrategy
					&& ObjectStrategy.class
							.isAssignableFrom(collectionAnnotation
									.collectionElementStrategy())
					&& Object.class.equals(componentType)) {
				LOG.debug("Element strategy is ObjectStrategy and array element is of type Object: using the ObjectStrategy strategy");
				arrayElement = elementStrategy.getValue();
			} else if (null != elementStrategy
					&& !ObjectStrategy.class
							.isAssignableFrom(collectionAnnotation
									.collectionElementStrategy())) {
				LOG.debug("Array elements will be filled using the following strategy: "
						+ elementStrategy);
				arrayElement = returnAttributeDataStrategyValue(componentType,
						elementStrategy);

			} else {

				arrayElement = manufactureAttributeValue(array, pojos,
						componentType, genericComponentType, annotations, attributeName,
						typeArgsMap, genericTypeArgs.get());

			}

			Array.set(array, i, arrayElement);

		}

		return array;
	}

	/**
	 * Given a collection type it returns an instance
	 *
	 * <ul>
	 * <li>The default type for a {@link List} is an {@link ArrayList}</li>
	 * <li>The default type for a {@link Queue} is a {@link LinkedList}</li>
	 * <li>The default type for a {@link Set} is a {@link HashSet}</li>
	 * </ul>
	 *
	 * @param collectionType
	 *            The collection type *
	 * @param defaultValue
	 *            Default value for the collection, can be null
	 * @return an instance of the collection type or null
	 */
	private Collection<? super Object> resolveCollectionType(
			Class<?> collectionType, Collection<? super Object> defaultValue) {

		Collection<? super Object> retValue = null;

		// Default list and set are ArrayList and HashSet. If users
		// wants a particular collection flavour they have to initialise
		// the collection
		if (null != defaultValue &&
				(defaultValue.getClass().getModifiers() & Modifier.PRIVATE) == 0) {
			/* Default collection, which is not immutable */
			retValue = defaultValue;
		} else {
			if (Queue.class.isAssignableFrom(collectionType)) {
				if (collectionType.isAssignableFrom(LinkedList.class)) {
					retValue = new LinkedList<Object>();
				}
			} else if (Set.class.isAssignableFrom(collectionType)) {
				if (collectionType.isAssignableFrom(HashSet.class)) {
					retValue = new HashSet<Object>();
				}
			} else {
				if (collectionType.isAssignableFrom(ArrayList.class)) {
					retValue = new ArrayList<Object>();
				}
			}
			if (null != retValue && null != defaultValue) {
				retValue.addAll(defaultValue);
			}
		}
		return retValue;
	}

	/**
	 * It manufactures and returns a default instance for each map type
	 *
	 * <p>
	 * The default implementation for a {@link ConcurrentMap} is
	 * {@link ConcurrentHashMap}
	 * </p>
	 *
	 * <p>
	 * The default implementation for a {@link SortedMap} is a {@link TreeMap}
	 * </p>
	 *
	 * <p>
	 * The default Map is none of the above was recognised is a {@link HashMap}
	 * </p>
	 *
	 * @param mapType
	 *            The attribute type implementing Map
	 * @param defaultValue
	 *            Default value for map
	 * @return A default instance for each map type or null
	 *
	 */
	private Map<? super Object, ? super Object> resolveMapType(
			Class<?> mapType, Map<? super Object, ? super Object> defaultValue) {

		Map<? super Object, ? super Object> retValue = null;

		if (null != defaultValue &&
				(defaultValue.getClass().getModifiers() & Modifier.PRIVATE) == 0) {
			/* Default map, which is not immutable */
			retValue = defaultValue;
		} else {
			if (SortedMap.class.isAssignableFrom(mapType)) {
				if (mapType.isAssignableFrom(TreeMap.class)) {
					retValue = new TreeMap<Object, Object>();
				}
			} else if (ConcurrentMap.class.isAssignableFrom(mapType)) {
				if (mapType.isAssignableFrom(ConcurrentHashMap.class)) {
					retValue = new ConcurrentHashMap<Object, Object>();
				}
			} else {
				if (mapType.isAssignableFrom(HashMap.class)) {
					retValue = new HashMap<Object, Object>();
				}
			}
		}

		return retValue;

	}

	/**
	 * Given a constructor it manufactures and returns the parameter values
	 * required to invoke it
	 *
	 * @param constructor
	 *            The constructor for which parameter values are required
	 * @param pojoClass
	 *            The POJO class containing the constructor
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 *
	 * @return The parameter values required to invoke the constructor
	 * @throws IllegalArgumentException
	 *             If an illegal argument was passed to the constructor
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 */
	private Object[] getParameterValuesForConstructor(
			Constructor<?> constructor, Class<?> pojoClass,
			Map<Class<?>, Integer> pojos, Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		final Map<String, Type> typeArgsMap = new HashMap<String, Type>();
		final Type[] genericTypeArgsExtra = fillTypeArgMap(typeArgsMap, pojoClass,
				genericTypeArgs);

		Annotation[][] parameterAnnotations = constructor
				.getParameterAnnotations();

		Class<?>[] parameterTypes = constructor.getParameterTypes();
		Type[] genericTypes = constructor.getGenericParameterTypes();
		Object[] parameterValues = new Object[parameterTypes.length];

		for (int idx = 0; idx < parameterTypes.length; idx++) {

			List<Annotation> annotations = Arrays
					.asList(parameterAnnotations[idx]);

			Type genericType = (idx < genericTypes.length) ?
					genericTypes[idx] : parameterTypes[idx];

			parameterValues[idx] = manufactureParameterValue(parameterTypes[idx],
					genericType, annotations, typeArgsMap, pojos,
					genericTypeArgsExtra == null ? NO_TYPES : genericTypeArgsExtra);
		}

		return parameterValues;

	}

	/**
	 * Manufactures and returns the parameter value for method required to
	 * invoke it
	 *
	 * @param parameterType type of parameter
	 * @param genericType generic type of parameter
	 * @param annotations parameter annotations
	 * @param typeArgsMap map for resolving generic types
	 * @param pojos
	 *            Set of manufactured pojos' types
	 * @param genericTypeArgs
	 *            The generic type arguments for the current generic class
	 *            instance
	 *
	 * @return The parameter values required to invoke the constructor
	 * @throws IllegalArgumentException
	 *             If an illegal argument was passed to the constructor
	 * @throws InstantiationException
	 *             If an exception occurred during instantiation
	 * @throws IllegalAccessException
	 *             If security was violated while creating the object
	 * @throws InvocationTargetException
	 *             If an exception occurred while invoking the constructor or
	 *             factory method
	 * @throws ClassNotFoundException
	 *             If it was not possible to create a class from a string
	 */
	private Object manufactureParameterValue(Class<?> parameterType,
			Type genericType, List<Annotation> annotations,
			final Map<String, Type> typeArgsMap, Map<Class<?>, Integer> pojos,
			Type... genericTypeArgs)
			throws InstantiationException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException {

		Object parameterValue = null;

		AttributeStrategy<?> attributeStrategy
				= findAttributeStrategy(annotations, parameterType);
		if (null != attributeStrategy) {

			LOG.debug("The parameter: " + genericType
					+ " will be filled using the following strategy: "
					+ attributeStrategy);

			return returnAttributeDataStrategyValue(parameterType,
					attributeStrategy);
		}

		if (Collection.class.isAssignableFrom(parameterType)) {

			Collection<? super Object> defaultValue = null;
			Collection<? super Object> collection = resolveCollectionType(
					parameterType, defaultValue);

			if (collection != null) {
				Class<?> collectionElementType;
				AtomicReference<Type[]> collectionGenericTypeArgs = new AtomicReference<Type[]>(
						NO_TYPES);
				if (genericType instanceof ParameterizedType) {
					ParameterizedType pType = (ParameterizedType) genericType;
					Type actualTypeArgument = pType.getActualTypeArguments()[0];

					collectionElementType = resolveGenericParameter(
							actualTypeArgument, typeArgsMap,
							collectionGenericTypeArgs);
				} else {
					LOG.warn("Collection parameter {} type is non-generic."
							+ "We will assume a Collection<Object> for you.",
							genericType);
					collectionElementType = Object.class;
				}

				Type[] genericTypeArgsAll = mergeTypeArrays(
						collectionGenericTypeArgs.get(), genericTypeArgs);
				String attributeName = null;
				fillCollection(pojos, annotations, attributeName,
						collection, collectionElementType, genericTypeArgsAll);

				parameterValue = collection;
			}

		} else if (Map.class.isAssignableFrom(parameterType)) {

			Map<? super Object, ? super Object> defaultValue = null;
			Map<? super Object, ? super Object> map = resolveMapType(parameterType, defaultValue);

			if (map != null) {
				Class<?> keyClass;
				Class<?> elementClass;
				AtomicReference<Type[]> keyGenericTypeArgs = new AtomicReference<Type[]>(
						NO_TYPES);
				AtomicReference<Type[]> elementGenericTypeArgs = new AtomicReference<Type[]>(
						NO_TYPES);
				if (genericType instanceof ParameterizedType) {
					ParameterizedType pType = (ParameterizedType) genericType;
					Type[] actualTypeArguments = pType.getActualTypeArguments();

					keyClass = resolveGenericParameter(actualTypeArguments[0],
							typeArgsMap, keyGenericTypeArgs);
					elementClass = resolveGenericParameter(
							actualTypeArguments[1], typeArgsMap,
							elementGenericTypeArgs);
				} else {
					LOG.warn("Map parameter {} type is non-generic."
							+ "We will assume a Map<Object,Object> for you.",
							genericType);
					keyClass = Object.class;
					elementClass = Object.class;
				}

				Type[] genericTypeArgsAll = mergeTypeArrays(
						elementGenericTypeArgs.get(), genericTypeArgs);

				MapArguments mapArguments = new MapArguments();
				mapArguments.setPojos(pojos);
				mapArguments.setAnnotations(annotations);
				mapArguments.setMapToBeFilled(map);
				mapArguments.setKeyClass(keyClass);
				mapArguments.setElementClass(elementClass);
				mapArguments.setKeyGenericTypeArgs(keyGenericTypeArgs.get());
				mapArguments.setElementGenericTypeArgs(genericTypeArgsAll);

				fillMap(mapArguments);

				parameterValue = map;
			}
		}

		if (parameterValue == null) {

			Map<String, Type> typeArgsMapForParam;
			if (genericType instanceof ParameterizedType) {
				typeArgsMapForParam = new HashMap<String, Type>(typeArgsMap);
				ParameterizedType parametrizedType =
						(ParameterizedType) genericType;

				TypeVariable<?>[] argumentTypes = parameterType.getTypeParameters();
				Type[] argumentGenericTypes = parametrizedType.getActualTypeArguments();

				for (int k = 0; k < argumentTypes.length; k++) {
					if (argumentGenericTypes[k] instanceof Class) {
						Class<?> genericParam = (Class<?>) argumentGenericTypes[k];
						typeArgsMapForParam.put(argumentTypes[k].getName(), genericParam);
					}
				}
			} else {
				typeArgsMapForParam = typeArgsMap;
			}

			parameterValue = manufactureParameterValue(pojos, parameterType,
					genericType, annotations, typeArgsMapForParam,
					genericTypeArgs);
		}

		return parameterValue;
	}

	/**
	 * Utility method to merge two arrays
	 *
	 * @param original
	 *            The main array
	 * @param extra
	 *            The additional array, optionally may be null
	 * @return A merged array of original and extra arrays
	 */
	private Type[] mergeTypeArrays(Type[] original, Type[] extra) {

		Type[] merged;

		if (extra != null) {
			merged = new Type[original.length + extra.length];
			System.arraycopy(original, 0, merged, 0, original.length);
			System.arraycopy(extra, 0, merged, original.length, extra.length);
		} else {
			merged = original;
		}

		return merged;
	}

	/**
	 * It retrieves the value for the {@link PodamStrategyValue} annotation with
	 * which the attribute was annotated
	 *
	 * @param attributeType
	 *            The attribute type, used for type checking
	 * @param attributeStrategy
	 *            The {@link AttributeStrategy} to use
	 * @return The value for the {@link PodamStrategyValue} annotation with
	 *         which the attribute was annotated
	 * @throws IllegalArgumentException
	 *             If the type of the data strategy defined for the
	 *             {@link PodamStrategyValue} annotation is not assignable to
	 *             the annotated attribute. This de facto guarantees type
	 *             safety.
	 */
	private Object returnAttributeDataStrategyValue(Class<?> attributeType,
			AttributeStrategy<?> attributeStrategy)
			throws IllegalArgumentException {

		Object retValue = attributeStrategy.getValue();

		if (retValue != null
				&& !attributeType.isAssignableFrom(retValue.getClass())) {
			String errMsg = "The type of the Podam Attribute Strategy is not "
					+ attributeType.getName() + " but "
					+ retValue.getClass().getName()
					+ ". An exception will be thrown.";
			LOG.error(errMsg);
			throw new IllegalArgumentException(errMsg);
		}

		return retValue;

	}

	@Override
	public ClassInfoStrategy getClassStrategy() {
		return classInfoStrategy;
	}

	@Override
	public PodamFactory setClassStrategy(ClassInfoStrategy classInfoStrategy) {
		this.classInfoStrategy = classInfoStrategy;
		return this;
	}

	// ------------------->> equals() / hashcode() / toString()

	// ------------------->> Inner classes

}
