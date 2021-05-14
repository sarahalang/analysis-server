package at.tugraz.oop2.client;

import at.tugraz.oop2.data.DataPoint;
import at.tugraz.oop2.data.DataSeries;
import at.tugraz.oop2.data.Sensor;
import org.apache.commons.math3.geometry.spherical.twod.Circle;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;

/* TODO: Command:
scatterplot 1000 P1 1000 P2 2018-01-01T00:00:00 2018-01-01T00:30:00 /app/scatterplot.png
scatterplot 1000 P1 1000 P2 2018-01-01T00:00:00 2018-01-01T00:00:01 /app/scatterplot.png
 */

public class ScatterPlot extends Diagram {

    public ScatterPlot() {}

    public void drawChart(DataSeries dataSensorA, DataSeries dataSensorB, DataPoint minA, DataPoint maxA, DataPoint minB, DataPoint maxB) {
        Double diffMinMaxValueX = maxA.getValue() - minA.getValue();
        Double scaleX = diagramDimensions.x / diffMinMaxValueX.doubleValue();

        Double diffMinMaxValueY = maxB.getValue() - minB.getValue();
        Double scaleY = diagramDimensions.y / diffMinMaxValueY;

        //System.out.println("Scale: " + scaleX + " | " + scaleY);
        //System.out.println("Dimensions: " + diagramDimensions.x + " | " + diagramDimensions.y);
        //System.out.println("Diff: " + diffMinMaxValueX + " | " + diffMinMaxValue);

        Iterator dataA = dataSensorA.iterator();
        Iterator dataB = dataSensorB.iterator();

        Stroke stroke = graphics2D.getStroke();
        graphics2D.setStroke(new BasicStroke(2));
        while (dataA.hasNext() && dataB.hasNext()) {
            DataPoint pointA = (DataPoint) dataA.next();
            DataPoint pointB = (DataPoint) dataB.next();

            Double valueX = pointA.getValue() - minA.getValue();
            int coordinateX = diagramOrigin.x + ((int) Math.round(valueX * scaleX));

            Double valueY = pointB.getValue() - minB.getValue();
            int coordinateY = diagramOrigin.y - ((int) Math.round(valueY * scaleY)) ;

            //System.out.println("X: " + coordinateX + " Y:" + coordinateY);
            graphics2D.drawLine(coordinateX, coordinateY, coordinateX, coordinateY);
        }
        graphics2D.setStroke(stroke);
        createAxisXLabeling(minA.getValue(), scaleX, dataSensorA.getSensor());
        createAxisYLabeling(minB.getValue(), scaleY, dataSensorB.getSensor());
        createAdditionalInformation(dataSensorA.getSensor(), dataSensorB.getSensor(), dataSensorA.first().getTime(), dataSensorA.last().getTime());
    }

    private void createAdditionalInformation(Sensor sensorA, Sensor sensorB, LocalDateTime startTime, LocalDateTime endTime) {
        graphics2D.drawString("SensorX: " + sensorA.getType() + " " + sensorA.getMetric(), 15, height - 50);
        graphics2D.drawString("SensorY: " + sensorB.getType() + " " + sensorB.getMetric(), 15, height - 35);
        graphics2D.drawString("Time: " + startTime + " - " + endTime, 15, height - 20);
    }
}
