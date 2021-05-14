package at.tugraz.oop2.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents one data point in a data series. The meaning of the
 * field <b>value</b> is defined in the class <b>DataSeries</b>.
 * <p>
 * For example, the meaning (metric) of the field <b>value</b> can be <b>°C</b>
 * for temperature, <b>%</b> for relative humidity, and more.
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class DataPoint implements Comparable<DataPoint>, Serializable {
    private final LocalDateTime time;
    private final Double value;

    @Override
    public int compareTo(DataPoint dataPoint) {
        int dateComparision = time.compareTo(dataPoint.getTime());
        return dateComparision == 0 ? Double.compare(this.getValue(), value) : dateComparision;
    }
}
