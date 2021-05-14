package at.tugraz.oop2.server;

import at.tugraz.oop2.Logger;
import at.tugraz.oop2.Util;
import at.tugraz.oop2.data.DataPoint;
import at.tugraz.oop2.data.Sensor;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import at.tugraz.oop2.data.DataSeries;

public class SensorManager {
    private static SensorManager instance;
    private final String dataPath;
    private HashMap<Integer, Vector<Sensor>> sensors = null;
    private HashMap<String, Vector<CacheElement>> cache = null; // Key is a String made up of ID and metric of sensor

    private SensorManager(String dataPath) {
        this.dataPath = dataPath + "/sensors"; //TODO: is this always legit? could the path be different than data? @ServerMain:14
        this.initSensorList();
    }

    public static void init(String dataPath) {
       SensorManager.instance = new SensorManager(dataPath);
    }
    public static SensorManager getInstance() {
        // make sure it is not called before init
        return SensorManager.instance;
    }

    private void initSensorList() {
       this.sensors = new HashMap<Integer, Vector<Sensor>>();
       this.cache = new HashMap<String, Vector<CacheElement>>();

       Logger.serverDiskRequest(this.dataPath);
       File root = new File(this.dataPath);
       File[] folders = root.listFiles();
       if (folders != null) {
           for(File folder : folders) {
               Pattern pattern = Pattern.compile("([0-9]{4}_[a-z0-9]+)", Pattern.CASE_INSENSITIVE);
               Matcher matcher = pattern.matcher(folder.getName());;
               if(!matcher.find()) {
                   Logger.warn("Folder '" + folder.getName() + "' is invalid.");
//                   System.out.println("Folder path invalid: " + folder.getName());
                   continue;
               }
               try {
//                   System.out.println("//---------------------------//");
                   File[] CSV_files = folder.listFiles();
                   if (CSV_files == null) {
//                       System.out.println("Folder empty");
                       Logger.warn("Folder '" + folder.getName() + "' is empty.");
                       continue;
                   }
                   String firstLine;
                   String secondLine;
                   String[] values1 = null;
                   String[] values2 = null;
                   int runner;
                   for(runner = 0; runner < CSV_files.length; runner++) {
                       if(Util.CSVFileNameIsValid(CSV_files[runner].getName())) {
                           // Since we only need the identifying values, skip after first valid file
                           firstLine = Files.readAllLines(CSV_files[runner].toPath(), Charset.defaultCharset()).get(0);
                           secondLine = Files.readAllLines(CSV_files[runner].toPath(), Charset.defaultCharset()).get(1);
                           values1 = firstLine.split(";", -1);
                           values2 = secondLine.split(";", -1);
                           if(values1.length == values2.length) {
                               break;
                           } else {
                               continue;
                           }
                       }
                   }
                   Vector<Sensor> instances = new Vector<Sensor>();
                   // check cells from 7+. if second line has value, metric with value from first line gets instanced
                   for (int i = 6, j = 6; i < values1.length && j < values2.length; i++, j++) {
                       if(!values2[i].isEmpty()) {
                           instances.add(new Sensor(Integer.parseInt(values2[0]), values2[1], Double.parseDouble(values2[3]),
                                   Double.parseDouble(values2[4]), values2[2], values1[i]));
                       }
                   }
                   sensors.put(Integer.parseInt(values2[0]), instances);
               } catch (Exception e) {
                   Logger.info(e.getMessage());
//                   System.out.println(e.getLocalizedMessage() + "-----" + e.getMessage());
               }
           }
       }
    }

    public List<Sensor> getSensorList() {
        if(sensors == null) {
            this.initSensorList();
        }
        List <Sensor> result = new ArrayList<Sensor>();
        sensors.values().forEach(result::addAll);
/*
        System.out.println("DEBUG: Sensorlist has " + sensors.size() + " Sensors.");
        System.out.println("DEBUG: Overall we have " + Integer.toString(result.size()) + " metrics instantiated");
        System.out.println("DEBUG: This makes " + String.format("%.4f" , ((float)result.size()/(float)sensors.size())) + " metrics per sensor on average");
*/
        return result;
    }

    private Sensor getSensorMetricByID(int id, String metric) {
        Vector<Sensor> instances = sensors.get(id);
        if (instances == null) { return new Sensor(id, "ERROR_SENSOR_NOT_FOUND", 0, 0, "NOWHERE", "NONE"); }
        for(Sensor sensor : instances) {
            if(sensor.getMetric().equals(metric))
                return sensor;
        };
        return new Sensor(id, "ERROR_METRIC_INVALID", 0, 0, "NOWHERE", "NONE");
    }

    public List<Integer> getAllSensorIdsByMetric(String metric) {
        List<Integer> result = new ArrayList<Integer>();
        if(sensors == null) {
            this.initSensorList();
        }
        for(Vector<Sensor> sensor : sensors.values()) {
            for (Sensor instance : sensor) {
                if(instance.getMetric().equals(metric)) {
                    result.add(instance.getId());
                }
            }
        }
        return result;
    }

    public void addToCache(String idString, LocalDateTime from, LocalDateTime to, DataSeries dataSeries) {
        Vector<CacheElement> cacheVector;

        // if no cache exists for this sensor, create sensor in Hashmap
        CacheElement stuffToCache = new CacheElement(from, to, dataSeries);

        if (cache.containsKey(idString)) {
           cacheVector = cache.get(idString);

           for (CacheElement elem : cacheVector) {
               if ( ( (elem.getFrom().equals(from)) || (elem.getFrom().isBefore(from)) )
                       && ( (elem.getTo().equals(to)) || (elem.getTo().isAfter(to))) ) {
                   // Cache Entry already exists
                   return;
               }
           }
           cacheVector.add(stuffToCache);
        } else {
            cacheVector = new Vector<CacheElement>();
            cacheVector.add(stuffToCache);
            cache.put(idString, cacheVector);
        }
    }

    public boolean sensorHasCache(String idString) {
        if (!cache.isEmpty()) {
            if (cache.containsKey(idString)) {
                return true; // A cache exists for the sensor in question
            }
        }
        return false;
    }

    public DataSeries queryCache(Sensor sensor, String idString, ClientCommand command) { //} LocalDateTime from, LocalDateTime to) {
        Vector<CacheElement> sensorCache = cache.get(idString);
        DataSeries result = new DataSeries(sensor, DataSeries.Operation.NONE, (int)(command.getFrom().until(command.getTo(), ChronoUnit.SECONDS))); //TODO stimmt die 1?

        for (CacheElement cacheEntry : sensorCache) {
            // First: Testing if the CacheElement (containg a DataSeries and from/to timestamps)
            // spans the time range in question
            if ( !cacheEntry.getFrom().isAfter(command.getFrom()) && !cacheEntry.getTo().isBefore(command.getTo())) {
                // then we have the ranged the user asked for
//                System.out.println("CACHE HIT");

                DataPoint fromPoint = getFirstDataPointAfterTimeStamp(cacheEntry.getDataSeries(), command.getFrom());
                DataPoint toPoint = getLastDataPointBeforeTimeStamp(cacheEntry.getDataSeries(), command.getTo());
                if ( (fromPoint == null) || (toPoint == null) ) {
                    // there was a problem finding suitable data points in the cache, thus returning
                    // and fetching the data normally from the CSV
                    return null;
                }
                TreeSet<DataPoint> subSet = (TreeSet<DataPoint>) cacheEntry.getDataSeries().subSet(fromPoint, toPoint);
                for (DataPoint point : subSet) {
                    result.add(point);
                }
                result.add(toPoint);
                return result;
            }
        }
        return null;
    }
    public DataPoint getFirstDataPointAfterTimeStamp(DataSeries data, LocalDateTime timestamp) {
        // TODO könnte Probleme machen, wenn das Cache Element die Werte nicht enthält, d.h. hierher darf es eig nur kommen, wenn es die Range geben sollte
        for (DataPoint point : data) {
            if ( (point.getTime().equals(timestamp)) || (point.getTime().isAfter(timestamp))) {
                return point;
            }
        }
        Logger.warn("No data point found after given timestamp!");
        return null;
    }
    public DataPoint getLastDataPointBeforeTimeStamp(DataSeries data, LocalDateTime timestamp) {
        // TODO könnte Probleme machen, wenn das Cache Element die Werte nicht enthält, d.h. hierher darf es eig nur kommen, wenn es die Range geben sollte
        DataPoint lastPoint = null;

        for (DataPoint point : data) {
            if (point.getTime().equals(timestamp)) {
                return point;
            }
            else if ((point.getTime().isAfter(timestamp)) && (lastPoint != null)) {
                return lastPoint;
            }
            lastPoint = point; // set last point so we can backtrack to it after we've passed it.
        }
//        System.out.println("No data point found after given timestamp!");
        return null;
    }

    public DataPoint getDataPointByTimeStamp(DataSeries data, LocalDateTime timestamp) {
        for (DataPoint point : data) {
            if (point.getTime().equals(timestamp)) {
                return point;
            }
        }
        return null;
    }


    public DataSeries getSensorData(ClientCommand command) { //Sensor sensor, LocalDateTime from, LocalDateTime to) {
        Sensor sensor = getSensorMetricByID(command.getID(), command.getMetric());
        DataSeries result = new DataSeries(sensor, DataSeries.Operation.NONE, (int)(command.getFrom().until(command.getTo(), ChronoUnit.SECONDS)));
        if (sensor.getType().contains("ERROR_")) { return result; }
        DataSeries cacheResult;//new DataSeries(sensor, command.getOperation(), command.getInterval());

        if (sensorHasCache(sensor.getIDString())) {
            cacheResult = queryCache(sensor, sensor.getIDString(), command);
            if (cacheResult != null) {
                Logger.serverCacheHit(sensor, command.getFrom(), command.getTo());
                return cacheResult;
            }
        }
        Logger.serverCacheMiss(sensor, command.getFrom(), command.getTo());

        cacheResult = new DataSeries(sensor, DataSeries.Operation.NONE, (int)(command.getFrom().until(command.getTo(), ChronoUnit.SECONDS))); // reset to fill

//        System.out.println("[Requested Data] Sensor ID: " + sensor.getId() + " for metric " + sensor.getMetric() + ". Requested timespan from "
//                            + from.toString() + " to " + to.toString());
        for(LocalDate date = command.getFrom().toLocalDate(); date.compareTo(command.getTo().toLocalDate()) < 1; date = date.plusDays(1)) {
        // extract Day from command, iterate through until end date (add 1 day) and use it to build path for files to read
            String path = this.dataPath + "/" + sensor.getId() + "_" + sensor.getType().toLowerCase() + "/" + //Folder Path maybe keep as separate variable?
                          date + "_" + sensor.getType().toLowerCase() + "_sensor_" + sensor.getId() + ".csv"; //File Path
//            System.out.println("Build file path to check: " + path);
            try {
                Logger.serverDiskRequest(path);
                Scanner file = new Scanner(new File(path));
                if(!file.hasNext()) {
                    Logger.warn("File is empty and has no lines.");
                    continue;
                }
                String[] firstLine = file.next().split(";");
                String[] buffer = null;
                boolean buffered = false;
                Double met_val = 0.0;
                int metricPos = Util.getMetricPosition(firstLine, sensor.getMetric());
                if(metricPos < 0) {
                    Logger.warn("Metric was not found.");
                    continue;
                } //metric not found wrong sensor type? should not happen I guess...
                String[] cells = null;
                while(file.hasNext()) { //go through all lines in file
                    if(buffered) {
                       cells = buffer;
                       buffered = false;
                    } else { cells = file.next().split(";", -1); }
                    if (cells.length != firstLine.length) { continue; } // skip erroneous entries
                    try {
                        if (date.equals(command.getFrom().toLocalDate()) || date.equals(command.getTo().toLocalDate())) {
                            LocalDateTime entryTime = LocalDateTime.parse(cells[5]);
                            if(entryTime.compareTo(command.getFrom()) > -1 && entryTime.compareTo(command.getTo()) < 0 ) { //equal or later than from and earlier than to
                                result.add(new DataPoint(LocalDateTime.parse(cells[5]), Double.parseDouble(cells[metricPos])));
//                                System.out.println("added " + cells[5] + " | " + cells[metricPos]);
                            }
                        } else {
                            result.add(new DataPoint(LocalDateTime.parse(cells[5]), Double.parseDouble(cells[metricPos])));
                        }
                    } catch (DateTimeParseException e) {
                        // wrong value format in cell[5] aka Timestamp
                        continue;
                    } catch (NumberFormatException e) {
                        // wrong value type in metric cell
                        Logger.warn("There was a wrong value type for the metric.");
                        buffer = file.next().split(";", -1);
                        try {
                            met_val = (Double.parseDouble(buffer[metricPos]) + result.last().getValue()) / 2;
                        } catch (Exception e1) {
                            met_val = result.last().getValue();
//                            return new DataSeries(new Sensor(command.getID(), "ERROR_TOO_MANY_MISSING_METRICS", 0, 0, "NOWHERE", "NONE"),
//                                    DataSeries.Operation.NONE,
//                                    (int)(command.getTo().toEpochSecond(ZoneOffset.of("Z")) - (command.getFrom().toEpochSecond(ZoneOffset.of("Z")))));
                        }
                        result.add(new DataPoint(LocalDateTime.parse(cells[5]), met_val));
                        buffered = true;
                    }// always cache the whole file while reading it
                    cacheResult.add(buffered ? new DataPoint(LocalDateTime.parse(cells[5]), met_val) : new DataPoint(LocalDateTime.parse(cells[5]), Double.parseDouble(cells[metricPos])));
                }
            } catch (FileNotFoundException e) {
//                react to non reachable file
                Logger.warn("File '" + path + "' was unreachable");
                continue;
//                return new DataSeries(new Sensor(command.getID(), "ERROR_FOLDER_UNREACHABLE", 0, 0, "NOWHERE", "NONE"),
//                                      DataSeries.Operation.NONE,
//                                      (int)(command.getTo().toEpochSecond(ZoneOffset.of("Z")) - (command.getFrom().toEpochSecond(ZoneOffset.of("Z")))));
            }
        }
        Logger.info("[DATA] " + result.size() + " Datapoints created");
        addToCache(sensor.getIDString(), command.getFrom(), command.getTo(), cacheResult);
        for (String key : cache.keySet()) {
            Logger.info("* For Server: " + key);
            for (CacheElement elem : cache.get(key)) {
                Logger.info("  -- Day from: " + elem.getFrom() + " - to: " + elem.getTo() + " = " + elem.getDataSeries().size() + " datapoints.");
            }
        }
        //System.out.println("/// CACHE ///");

        return !result.isEmpty() ? result :
                new DataSeries(new Sensor(command.getID(), "ERROR_SENSOR_WAS_NOT_FOUND", 0, 0, "NOWHERE", "NONE"),
                DataSeries.Operation.NONE,
                (int)(command.getTo().toEpochSecond(ZoneOffset.of("Z")) - (command.getFrom().toEpochSecond(ZoneOffset.of("Z")))));
    }
}
