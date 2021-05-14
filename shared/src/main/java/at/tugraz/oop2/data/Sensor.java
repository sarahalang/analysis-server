package at.tugraz.oop2.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Holds the combination of a location and a metric of a sensor.
 * <p>
 * One "real" sensor measures many metrics (e.g. pressure, humidity, and temperature),
 * but this class only represents one combination of location and metric. A "real" sensor
 * that measures three metrics is represented with three instances of this class.
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class Sensor implements java.io.Serializable {

    private final int id;
    private final String type;
    private final double latitude, longitude;
    private final String location, metric;

    public String prettyString() {
        return String.format("%s - %s", getLocation(), getMetric());
    }
    public void printToStdOut() { System.out.println(this.toString()); }
    public String getIDString() { return Integer.toString(id) + metric; }

}
