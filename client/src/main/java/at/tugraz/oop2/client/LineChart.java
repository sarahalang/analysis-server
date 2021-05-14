package at.tugraz.oop2.client;

import at.tugraz.oop2.data.DataPoint;
import at.tugraz.oop2.data.DataSeries;
import at.tugraz.oop2.data.Sensor;

import java.time.*;

/* TODO: Command:
linechart 4795 temperature 2018-01-01T00:00:00 2018-02-01T00:30:00 /app/linechart.png MIN 1m
linechart 4795 temperature 2018-01-01T00:00:00 2018-01-03T00:30:00 /app/linechart.png
linechart 3841 P1 2018-01-01T00:00:00 2018-01-03T00:30:00 /app/linechart.png
 */

public class LineChart extends Diagram {

    public LineChart() {}

    public void drawChart(DataSeries data, DataPoint min, DataPoint max, DataPoint mean) {
        int[] valuesX = new int[data.size()];
        int[] valuesY = new int[data.size()];
        ZoneOffset zone = ZoneOffset.of("Z");

        Long startValueX = data.first().getTime().toEpochSecond(zone);
        Long endValueX = data.last().getTime().toEpochSecond(zone);
        Long diffMinMaxValueX = endValueX - startValueX;
        Double scaleX = diagramDimensions.x / diffMinMaxValueX.doubleValue();

        Double diffMinMaxValue = max.getValue() - min.getValue();
        Double scaleY = diagramDimensions.y / diffMinMaxValue;

        //System.out.println("Scale: " + scaleX + " | " + scaleY);
        //System.out.println("Dimensions: " + diagramDimensions.x + " | " + diagramDimensions.y);
        //System.out.println("Diff: " + diffMinMaxValueX + " | " + diffMinMaxValue);

        int i = 0;
        for (DataPoint point : data) {
            Long timeDiff = point.getTime().toEpochSecond(zone) - startValueX;
            valuesX[i] = diagramOrigin.x + ((int) Math.round(timeDiff * scaleX)) ;

            Double value = (point.getValue() - min.getValue());
            valuesY[i] = diagramOrigin.y - ((int) Math.round(value * scaleY)) ;
            i++;
        }
        graphics2D.drawPolyline(valuesX, valuesY, valuesX.length);
        createAxisXTimeLabeling(startValueX, data.getInterval(), scaleX);
        createAxisYLabeling(min.getValue(), scaleY, data.getSensor());
        createAdditionalInformation(data.getSensor(), data.first().getTime(), data.last().getTime(), min, max, mean);
    }

    private void createAdditionalInformation(Sensor sensor, LocalDateTime startTime, LocalDateTime endTime, DataPoint min, DataPoint max, DataPoint mean) {
        graphics2D.drawString("Sensor: " + sensor.getType() + " " + sensor.getMetric(), 15, height - 50);
        graphics2D.drawString("Min: " + min.getValue() + " Max: " + max.getValue() + " Mean: " + mean.getValue(), 15, height - 35);
        graphics2D.drawString("Time: " + startTime + " - " + endTime, 15, height - 20);
    }
}
