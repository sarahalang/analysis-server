package at.tugraz.oop2.client;

import at.tugraz.oop2.data.DataPoint;
import at.tugraz.oop2.data.DataSeries;
import at.tugraz.oop2.data.Sensor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.awt.Color;
import java.awt.FontMetrics;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class Diagram {

    protected static final int width = 800;
    protected static final int height = 600;
    protected final BufferedImage bufferedImage;
    protected final Graphics2D graphics2D;

    protected Point diagramDimensions, diagramOrigin;
    protected Font rotatedFont, font;

    public Diagram() {
        bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        graphics2D = bufferedImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics2D.setColor(Color.white);
        graphics2D.fillRect(0,0, width, height);

        diagramDimensions = new Point();
        diagramOrigin = new Point();

        font = new Font("rotated", Font.PLAIN, 10);
        AffineTransform affineTransform = new AffineTransform();
        affineTransform.rotate(-Math.PI/2, 0, 0);
        rotatedFont = font.deriveFont(affineTransform);
        graphics2D.setFont(font);
    }

    public void initDiagram(){
        int marginLeftAxis = 100;
        int marginTopAxisX = height - 100;
        int marginTopAxisY = 50;
        int marginRightAxis = 700;

        diagramOrigin.x = marginLeftAxis;
        diagramOrigin.y = marginTopAxisX;
        diagramDimensions.x = marginRightAxis - marginLeftAxis;
        diagramDimensions.y = marginTopAxisX - marginTopAxisY;

        graphics2D.setColor(Color.black);
        graphics2D.drawLine(marginLeftAxis, marginTopAxisY, marginLeftAxis, marginTopAxisX);     // Y-Axis
        graphics2D.drawLine(marginLeftAxis, marginTopAxisX, marginRightAxis, marginTopAxisX);    // X-Axis
    }

    protected void createAxisXTimeLabeling(Long startTime, int interval, Double scaleX)
    {
        int lineLength = 10;
        int numberOfLabels = 5;
        int gap = (int)Math.round(diagramDimensions.x / (double)numberOfLabels);

        FontMetrics fontMetrics = graphics2D.getFontMetrics();

        for (int i = 0; i<5; i++) {

            long label = startTime + (Math.round((gap * i) / scaleX));
            LocalDateTime triggerTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(label), TimeZone.getDefault().toZoneId());

            String labelText, dateFormat;
            dateFormat = (interval > 200000) ? "dd-MM-yyyy HH:mm" : "HH:mm"; // select Format of Axis labels
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
            labelText = formatter.format(triggerTime);
            dateFormat = "[" + dateFormat + "]";

            graphics2D.drawLine(diagramOrigin.x + (gap * i), diagramOrigin.y, diagramOrigin.x + (gap * i), diagramOrigin.y + lineLength); // draw marks
            graphics2D.drawString(labelText, diagramOrigin.x + (gap * i) - (fontMetrics.stringWidth(labelText) / 2), diagramOrigin.y + lineLength + 10);
            graphics2D.drawString(dateFormat, (width / 2) - (fontMetrics.stringWidth(dateFormat) / 2), 580);
        }
    }

    protected void createAxisXLabeling(Double minValue, Double scaleX, Sensor sensor) {
        int lineLength = 10;
        int numberOfLabels = 5;
        int gap = (int)Math.round(diagramDimensions.x / (double)numberOfLabels);

        FontMetrics fontMetrics = graphics2D.getFontMetrics();

        for (int i = 0; i<=5; i++) {

            Double label = minValue + ((gap * i) / scaleX);
            label =(Math.round(label * 100.0) / 100.0);
            String labelText = label.toString();

            String unit = "[" + sensor.getMetric() + "]";
            graphics2D.drawLine(diagramOrigin.x + (gap * i), diagramOrigin.y, diagramOrigin.x + (gap * i), diagramOrigin.y + lineLength); // draw marks
            graphics2D.drawString(labelText, diagramOrigin.x + (gap * i) - (fontMetrics.stringWidth(labelText) / 2), diagramOrigin.y + lineLength + 10);
            graphics2D.drawString(unit, (width / 2) - (fontMetrics.stringWidth(unit) / 2), 580);
        }
    }

    protected void createAxisYLabeling(Double minValue, Double scaleY, Sensor sensor) {
        int lineLength = 10;
        int numberOfLabels = 5;
        int gap = (int)Math.round(diagramDimensions.y / (double) numberOfLabels);
        FontMetrics fontMetrics = graphics2D.getFontMetrics();

        for (int i = 0; i<=5; i++) {
            graphics2D.drawLine(diagramOrigin.x, diagramOrigin.y - (gap * i), diagramOrigin.x - lineLength, diagramOrigin.y - (gap * i)); // draw marks

            Double label = minValue + ((gap * i) / scaleY);
            label =(Math.round(label * 100.0) / 100.0);
            graphics2D.drawString(label.toString(), diagramOrigin.x  - lineLength - 10 - fontMetrics.stringWidth(label.toString()), diagramOrigin.y - (gap * i) + 4);
        }

        graphics2D.setFont(rotatedFont);
        String unit = "[" + sensor.getMetric() + "]";
        graphics2D.drawString(unit, 30, diagramOrigin.y - (diagramDimensions.y / 2) + (fontMetrics.stringWidth(unit) / 2));
        graphics2D.setFont(font);
    }

    protected Double[] calcMinMax(DataSeries dataSeries) {
        Double min = dataSeries.first().getValue();
        Double max = dataSeries.first().getValue();

        for (DataPoint dataPoint : dataSeries) {
            Double v = dataPoint.getValue();
            if(v < min) min = v;
            if(v > max) max = v;
        }
        return new Double[]{min, max};
    }

    public Graphics2D getGraphics2D() {
        return graphics2D;
    }

    public void save(String path) throws IOException {
        File file = new File(path);
        ImageIO.write(bufferedImage, "png", file);
    }

}
