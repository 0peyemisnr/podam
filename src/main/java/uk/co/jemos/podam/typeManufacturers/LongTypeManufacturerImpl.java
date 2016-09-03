package uk.co.jemos.podam.typeManufacturers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.jemos.podam.api.AttributeMetadata;
import uk.co.jemos.podam.api.DataProviderStrategy;
import uk.co.jemos.podam.common.PodamConstants;
import uk.co.jemos.podam.common.PodamLongValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Default int type manufacturer.
 *
 * Created by tedonema on 17/05/2015.
 *
 * @since 6.0.0.RELEASE
 */
public class LongTypeManufacturerImpl extends AbstractTypeManufacturer<Long> {

    /** The application logger */
    private static final Logger LOG = LoggerFactory.getLogger(LongTypeManufacturerImpl.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getType(DataProviderStrategy strategy,
            AttributeMetadata attributeMetadata,
            Map<String, Type> genericTypesArgumentsMap) {

        Long retValue = null;

        for (Annotation annotation : attributeMetadata.getAttributeAnnotations()) {

            if (PodamLongValue.class.isAssignableFrom(annotation.getClass())) {
                PodamLongValue longStrategy = (PodamLongValue) annotation;

                String numValueStr = longStrategy.numValue();
                if (null != numValueStr && !numValueStr.isEmpty()) {
                    try {
                        retValue = Long.valueOf(numValueStr);
                    } catch (NumberFormatException nfe) {
                        String errMsg = PodamConstants.THE_ANNOTATION_VALUE_STR
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
}
