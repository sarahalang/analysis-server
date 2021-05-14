package at.tugraz.oop2;

import at.tugraz.oop2.data.DataPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds some constants and useful methods.
 */
public final class Util {


    private Util() {

    }

    public static LocalDateTime stringToLocalDateTime(String datetime) {
        try {
            return LocalDate.parse(datetime, DateTimeFormatter.ISO_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(datetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Invalid date format " + datetime);
    }

    public static int stringToInterval(String interval) {
        if (interval.length() < 2) {
            throw new IllegalArgumentException("Invalid interval format " + interval);
        }
        try {
            int intervalPart = Integer.parseUnsignedInt(interval.substring(0, interval.length() - 1));
            char multiplicatorPart = interval.charAt(interval.length() - 1);
            int multiplicator = 1;

            switch (multiplicatorPart) {
                case 's':
                    multiplicator = 1;
                    break;
                case 'm':
                    multiplicator = 60;
                    break;
                case 'h':
                    multiplicator = 60 * 60;
                    break;
                case 'd':
                    multiplicator = 60 * 60 * 24;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid interval format " + interval);
            }

            return intervalPart * multiplicator;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid interval format " + interval);
        }

    }

    public static boolean CSVFileNameIsValid(String filename) {
        Pattern pattern = Pattern.compile("([0-9-]{10}_[a-z0-9]+(?:_sensor_)[0-9]{4}(?:.csv))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(filename);
        return matcher.find();
    }

    public static LocalDate getDateFromCSVname(String filename) {
        return LocalDate.parse(filename.split("_")[0]);
    }

    public static int getMetricPosition(String[] columnNamesLineValues, String metric) {
        for(int i = 0; i < columnNamesLineValues.length; i++) {
            if(columnNamesLineValues[i].equals(metric))
                return i;
        }
        return -1;
    }
}