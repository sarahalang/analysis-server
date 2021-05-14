package at.tugraz.oop2.server;

import at.tugraz.oop2.Logger;

import at.tugraz.oop2.data.*;
import at.tugraz.oop2.data.Sensor;

import java.io.*;
import java.net.Socket;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static at.tugraz.oop2.server.ClientCommand.TYPE.*;

public class ClientHandlerThread extends Thread {
    private Socket socket;
    private DataInputStream dataInputStream;
    private static ObjectOutputStream objectOutputStream;

    public ClientHandlerThread(Socket socket_c, DataInputStream dataInputStream, ObjectOutputStream objectOutputStream) {
        this.socket = socket_c;
        this.dataInputStream = dataInputStream;
        this.objectOutputStream = objectOutputStream;
    }

    public void run () {

        while(true)
        {
            try {
                String received = dataInputStream.readUTF();
                ClientCommand command = new ClientCommand(received);
                executeCommand(command);
            } catch (IOException e)
            {
                Logger.err("Connection to client refused!");
                break;
            }
        }
    }

    private void executeCommand(ClientCommand command) throws IOException {
        if (command.getCmd() == ERROR) {
            Logger.err("The inputted command was invalid.");
            return;
        }
        if (command.getCmd() == LS) {
            Logger.serverRequestLS();
            List<Sensor> sensors = SensorManager.getInstance().getSensorList();
            Logger.serverResponseLS(sensors);
            objectOutputStream.writeObject(sensors);
            return;
        }
        if (command.getCmd() == DATA) {
            DataQueryParameters dataQueryParameters = new DataQueryParameters(command.getID(),
                    command.getMetric(),
                    command.getFrom(), command.getTo(),
                    command.getOperation(),
                    command.getInterval());
            Logger.serverRequestData(dataQueryParameters);

            DataSeries dataSeries = executeDataCommand(command);
            objectOutputStream.writeObject(dataSeries);
            return;
        }
        if(command.getCmd() == CLUSTER) {
            SOMQueryParameters somQueryParameters = new SOMQueryParameters(command.getIds(),
                    command.getMetric(),
                    command.getFrom(),
                    command.getTo(),
                    command.getOperation(),
                    command.getInterval(),
                    command.getLength(),
                    command.getGridHeight_(),
                    command.getGridWidth_(),
                    command.getUpdateRadius_(),
                    command.getLearningRate_(),
                    command.getItsPerCurve_(),
                    command.getRes_id_(),
                    command.getInt_res_()
                    );
            Logger.serverRequestCluster(somQueryParameters);

            if((command.getTo().until(command.getFrom(), ChronoUnit.SECONDS) / command.getInterval()) % command.getLength() != 0) {
                errorReturn("ERROR_LOOSE_DATAPOINTS");
                return;
            }
            List<DataSeries> somData = new ArrayList<>();
            if (command.getIds() != null) { // list of IDs to be queried is filled
                // this will create a new ClientCommand object for each sensor id
                for (Integer oneSensorID : somQueryParameters.getSensorIds()) {

                    ClientCommand subCommandForSingleSensor = new ClientCommand(CLUSTER,
                            command.getMetric(), oneSensorID,
                            command.getFrom(), command.getTo(),
                            command.getOperation(),
                            command.getInterval());

                    DataSeries dataSeries = executeDataCommand(subCommandForSingleSensor);
                    if(dataSeries.size() % command.getLength() != 0) { //this should not happen because covered @line:88
                        System.out.println("Loose Datapoints in this command!");
                    }
                    if(dataSeries.isEmpty()) {
                        // empty data series means something went wrong. type holds error
//                        System.out.print(dataSeries.getSensor().getType() + " error handling switch ");
                        switch (dataSeries.getSensor().getType()) {
                            case "ERROR_SENSOR_NOT_FOUND":
                                somData.add(new DataSeries(new Sensor(oneSensorID, "WARN_SENSOR[" + oneSensorID + "]_ID_NOT_FOUND", 0,0,"NOWHERE","NONE"),
                                        command.getInterval(), command.getOperation()));
                                break;
                            case "ERROR_METRIC_INVALID":
                                somData.add(new DataSeries(new Sensor(oneSensorID, "WARN_SENSOR[" + oneSensorID +"]_DOES_NOT_HAVE_METRIC_" + command.getMetric().toUpperCase(),
                                        0,0,"NOWHERE", command.getMetric()), command.getInterval(), command.getOperation()));
                                break;
                            default:
                                errorReturn(dataSeries.getSensor().getType());
                                return;
                        }
                    } else if (dataSeries.getSensor().getType().contains("WARN"))
                        somData.add(dataSeries);
                    else
                        somData.addAll(cutDataIntoPieces(dataSeries, command.getLength()));
//                    System.out.println("[" + Integer.toString(oneSensorID) + "] data fetched. ERROR: " + dataSeries.getSensor().getType());
//                    System.out.println("Dataseries " + somData.size() + " has " + dataSeries.size() + " Elements.");
                }
            }  else {
                assert true : "ClientCommand Ids Array was null! This error is critcal and should not happen!";
            }

            if (somData.size() != 0) {
                SOMHandler somHandler = new SOMHandler(somData, command.getGridHeight_(),
                        command.getGridWidth_(), command.getLearningRate_(), command.getUpdateRadius_(),
                        command.getItsPerCurve_(), command.getInt_res_(), somQueryParameters);
//                System.out.println("SOMHandler created! Make magic happen here!");
                List<ClusterDescriptor> response = somHandler.clustering();

                Logger.serverResponseCluster(somQueryParameters);
                objectOutputStream.writeObject(response);
            }
        }
    }

    public static void warnReturn(String msg) {

        List<ClusterDescriptor> response =  new ArrayList<>();
         Logger.warn(msg);
        response.add(new ClusterDescriptor(0, 0, null,
                Collections.singletonList(new DataSeries(new Sensor(0, msg, 0, 0, "NOWHERE", "NONE"), 0, DataSeries.Operation.NONE))));
        try {
            objectOutputStream.writeObject(response);
        } catch (IOException e) {
            // what if that happens?
        }
    }

    public static void errorReturn(String msg) {

        List<ClusterDescriptor> response =  new ArrayList<>();
        Logger.err(msg);
        response.add(new ClusterDescriptor(0, 0, null,
                Collections.singletonList(new DataSeries(new Sensor(0, msg, 0, 0, "NOWHERE", "NONE"), 0, DataSeries.Operation.NONE))));
        try {
            objectOutputStream.writeObject(response);
        } catch (IOException e) {
            // what if that happens?
        }
    }

    public static void sendIntermediateResult(List<ClusterDescriptor> clusters, SOMQueryParameters params, int iteration) {
        Logger.serverIntermediateResponse(params, iteration);
        try {
            objectOutputStream.writeObject(clusters);
        } catch (IOException e) {
            // what if that happens?
        }
    }

    private List<DataSeries> cutDataIntoPieces(DataSeries dataSeries, int length) {
        Iterator<DataPoint> it = dataSeries.iterator();
        List<DataSeries> result = new ArrayList<DataSeries>();
        for(int i = 0; i < (dataSeries.size() / length); i++) {
            int j = 0;
            DataSeries tmp = new DataSeries(dataSeries.getSensor(), dataSeries.getOperation(), dataSeries.getInterval());
            while(it.hasNext() && j++ < length) { tmp.add(it.next()); }
            result.add(tmp);
        }
        return result;
    }


    private DataSeries executeDataCommand(ClientCommand command) {
        DataSeries dataSeries = SensorManager.getInstance().getSensorData(command);
        if(!dataSeries.isEmpty()) {
            if(command.getOperation() != DataSeries.Operation.NONE) {
                dataSeries = runIntervalBasedOperation(dataSeries, command);
            }
        }
        return dataSeries;
    }

    private DataSeries runIntervalBasedOperation(DataSeries dataSeries, ClientCommand command) {
//        System.out.println("Dataseries [" + dataSeries.getInterval() + "] with " + dataSeries.size() + " elements. From: " + dataSeries.first().getTime() + " to: " + dataSeries.last().getTime());
//        System.out.println("Math says we should end up with " + dataSeries.getInterval() / command.getInterval() + " values");
        DataPoint startPoint = null;
        DataPoint endPoint = dataSeries.first();
        DataSeries result = new DataSeries(dataSeries.getSensor(), command.getOperation(), command.getInterval());
        int runs = 0;
        do {
            startPoint = endPoint;
            LocalDateTime border = command.getFrom().plusSeconds(command.getInterval() * ++runs);
//            System.out.println("Searching in subset with " + dataSeries.tailSet(startPoint).size() + " Elements.");
            for(DataPoint point : dataSeries.tailSet(startPoint)) {
//                System.out.println("Border time should be: " + border + " Current Point Time " + point.getTime());
                if (!point.getTime().isBefore(border)) {
//                    System.out.println("new end point found");
                    endPoint = point;
                    break;
                }
            }
            DataSeries tmp = new DataSeries(dataSeries.getSensor(), command.getOperation(), command.getInterval());
            if(startPoint == endPoint) {
                tmp.addAll(dataSeries.tailSet(startPoint));
//                System.out.println(startPoint + "   ----   " + dataSeries.first().getTime().plusSeconds(dataSeries.getInterval()));
            } else {
                tmp.addAll(dataSeries.subSet(startPoint, endPoint));
            }
            result.add(executeOperation(tmp));
        } while(startPoint.getTime().plusSeconds(command.getInterval()).isBefore(dataSeries.last().getTime()));
//        System.out.println("Returning Data Series with " + result.size() + " elements");
        return ((dataSeries.getInterval() / command.getInterval()) <= result.size()) ? result :
                        fillMissingValues(result, command, (int)Math.ceil(dataSeries.getInterval() / command.getInterval()));
    }

    private DataPoint executeOperation(DataSeries dataSeries) {
        switch(dataSeries.getOperation()) {
            case MIN:
                DataPoint minDataPoint = dataSeries.first();

                for(DataPoint dataPoint : dataSeries) {
                    if(minDataPoint.getValue() > dataPoint.getValue()) {
                        minDataPoint = dataPoint;
                    }
                }
                return minDataPoint;
            case MAX:
                DataPoint maxDataPoint = dataSeries.first();

                for(DataPoint dataPoint : dataSeries) {
                    if(maxDataPoint.getValue() < dataPoint.getValue()) {
                        maxDataPoint = dataPoint;
                    }
                }
                return maxDataPoint;
            case MEAN:
                double sumOfValues = 0.0;
                for(DataPoint dataPoint : dataSeries) {
                    sumOfValues += dataPoint.getValue();
                }
                double mean = sumOfValues / dataSeries.size();

                LocalDateTime finalTime = dataSeries.first().getTime().plusSeconds(dataSeries.first().getTime().until(dataSeries.last().getTime(), ChronoUnit.SECONDS) / 2);
                return new DataPoint(finalTime, mean);
            case MEDIAN:
                List<Double> sorted_values = new ArrayList<>();

                for(DataPoint dataPoint : dataSeries) {
                    sorted_values.add(dataPoint.getValue());
                }
                Collections.sort(sorted_values);

                double medianValue = 0.0;
                if(dataSeries.size() % 2 == 0) {
                    int middleValue = ((dataSeries.size()/2)-1);
                    int middleValue2 = ((dataSeries.size()/2));
                    double temp = sorted_values.get(middleValue);
                    double temp2 = sorted_values.get(middleValue2);
                    medianValue = (temp+temp2)/2;

                    int count = 0;
                    LocalDateTime firstDate = null;
                    LocalDateTime secondDate = null;
                    for(DataPoint dataPoint : dataSeries) {
                        if(count == middleValue) {
                            firstDate = dataPoint.getTime();
                        }
                        if(count == middleValue2) {
                            secondDate = dataPoint.getTime();
                            break;
                        }
                        count++;
                    }
                    ZoneOffset zone = ZoneOffset.of("Z");
                    Long firstDateLong = firstDate.toEpochSecond(zone);
                    Long secondDateLong = secondDate.toEpochSecond(zone);
                    Long newDateLong = (firstDateLong + secondDateLong) / 2;
                    LocalDateTime newDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(newDateLong), TimeZone.getDefault().toZoneId());
                    return new DataPoint(newDate, medianValue);
                } else {
                    medianValue = sorted_values.get(dataSeries.size()/2);
                    for(DataPoint dataPoint : dataSeries) {
                        if(dataPoint.getValue() == medianValue) {
                            return dataPoint;
                        }
                    }
                }
            case NONE:
            default:
                return null;
        }
    }

    private DataSeries fillMissingValues(DataSeries dataSeries, ClientCommand command, int expected_count) {
        if(expected_count - dataSeries.size() != 1) { //if more than one value is missing, throw error TODO: is this valid for assignment 2?
//            System.out.println("There are " + (expected_count - dataSeries.size()) + " missing values");
            if(command.getCmd() != CLUSTER) {
                return (new DataSeries(new Sensor(dataSeries.getSensor().getId(), "ERROR_TOO_MANY_VALUES_MISSING", 0, 0, "NOWHERE", "NONE"),
                        dataSeries.getOperation(), dataSeries.getInterval()));
            }
        }
        if(command.getFrom().until(dataSeries.first().getTime(), ChronoUnit.SECONDS) > command.getInterval()) { // if data is missing at the beginning of the series
//            System.out.println("Generating first point for series");
            dataSeries.add(new DataPoint(command.getFrom().plusSeconds(command.getFrom().until(dataSeries.first().getTime(), ChronoUnit.SECONDS) / 2), dataSeries.first().getValue()));
            return ((expected_count - dataSeries.size()) == 0) ? dataSeries : fillMissingValues(dataSeries, command, expected_count);
//            return dataSeries;
        } else if (dataSeries.last().getTime().until(command.getTo(), ChronoUnit.SECONDS) > command.getInterval()) {    // if data is missing at the end of the series
//            System.out.println("Generating last point for series");
            dataSeries.add(new DataPoint(dataSeries.last().getTime().plusSeconds(dataSeries.last().getTime().until(command.getTo(), ChronoUnit.SECONDS) / 2), dataSeries.last().getValue()));
            return ((expected_count - dataSeries.size()) == 0) ? dataSeries : fillMissingValues(dataSeries, command, expected_count);
//            return dataSeries;
//        dataSeries.last().getTime().until(command.getTo(), ChronoUnit.SECONDS) > command.getInterval()) {    // we return with an error
//            return (new DataSeries(new Sensor(dataSeries.getSensor().getId(), "ERROR_VALUES_MISSING_AT_END", 0, 0, "NOWHERE", "NONE"),
//                    dataSeries.getOperation(), dataSeries.getInterval()));
        }
        // if data is missing somewhere in between
        double diff = -1;
        Iterator<DataPoint> it = dataSeries.iterator();
        DataPoint firstPoint = dataSeries.first();
        DataPoint secondPoint = dataSeries.first();
        DataPoint lastPoint = dataSeries.first();
        LocalDateTime date = it.next().getTime();
        while(it.hasNext()){
           DataPoint currentPoint = it.next();
           if(diff < (date.until(currentPoint.getTime(), ChronoUnit.SECONDS))) {
              diff = (date.until(currentPoint.getTime(), ChronoUnit.SECONDS));
              firstPoint = lastPoint;
              secondPoint = currentPoint;
           }
           date = currentPoint.getTime();
           lastPoint = currentPoint;
        }
        if(firstPoint == secondPoint) { // something somewhere went terrible wrong. this should not happen (I think)
            return (new DataSeries(new Sensor(dataSeries.getSensor().getId(), "ERROR_TOO_MANY_VALUES_MISSING_2", 0, 0, "NOWHERE", "NONE"),
                    dataSeries.getOperation(), dataSeries.getInterval()));
        }
/*
        int hours = (int)diff / 3600;
        int minutes = ((int)diff % 3600) / 60;
        int seconds = (int)diff % 60;
        System.out.println("Biggest diff as number: " + diff + " as time: " + String.format("%02d:%02d:%02d", hours, minutes, seconds));
        System.out.println("Found between point [" + firstPoint.getTime() + " | " + firstPoint.getValue() + "] and [" + secondPoint.getTime() + " | " + secondPoint.getValue() + "]");
        System.out.println("generated value: [" + firstPoint.getTime().plusSeconds(firstPoint.getTime().until(secondPoint.getTime(), ChronoUnit.SECONDS) / 2) + " | " + (firstPoint.getValue() + secondPoint.getValue())/2 + "]");
*/
//        System.out.println("Generating random middle point for series");
        dataSeries.add(new DataPoint(firstPoint.getTime().plusSeconds(firstPoint.getTime().until(secondPoint.getTime(), ChronoUnit.SECONDS) / 2),(firstPoint.getValue() + secondPoint.getValue()) / 2));
        return ((expected_count - dataSeries.size()) == 0) ? dataSeries : fillMissingValues(dataSeries, command, expected_count);
    }
}
