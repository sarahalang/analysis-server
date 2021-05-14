package at.tugraz.oop2.client;

import at.tugraz.oop2.Logger;
import at.tugraz.oop2.Util;
import at.tugraz.oop2.data.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used for handling and parsing commands. There is little to no work to do for you here, since
 * the CLI is already implemented.
 */
public final class CommandHandler {
    private static final String MSG_HELP = "Type 'help' for a list of commands.";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private final ClientConnection conn;
    private final Map<String, Command> commands = new HashMap<>();

    public CommandHandler(ClientConnection conn) {
        this.conn = conn;
        commands.put("help", this::displayHelp);
        commands.put("ls", this::listSensors);
        commands.put("data", this::queryData);
        commands.put("linechart", this::queryLineChart);
        commands.put("scatterplot", this::queryScatterplot);
        commands.put("cluster", this::queryCluster);
        commands.put("listresults", this::listFinishedClusteringResults);
        commands.put("rm", this::removeClusteringQueryResult);
        commands.put("inspectcluster", this::inspectCluster);
    }

    private static void validateArgc(String[] args, int argc) throws CommandException {
        if (args.length != argc) {
            throw new CommandException("Invalid usage. " + MSG_HELP);
        }
    }

    private static void validateArgc(String[] args, int minArgc, int maxArgc) throws CommandException {
        if (args.length < minArgc || args.length > maxArgc) {
            throw new CommandException("Invalid usage. " + MSG_HELP);
        }
    }

    private static void printDataPoint(DataPoint point) {
        System.out.println("\t" + point.getTime() + "\t" + point.getValue());
    }

    public void handle(String... args) {
        final Command cmd = commands.get(args[0].toLowerCase());
        if (cmd == null) {
            System.out.println("Unknown command. " + MSG_HELP);
            return;
        }
        try {
            cmd.handle(Arrays.copyOfRange(args, 1, args.length));
        } catch (final NumberFormatException ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        } catch (final CommandException ex) {
            interfaceErr(ex.getMessage());
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
    }

    public void openCLI() {
        System.out.println("Welcome to the command line interface. " + MSG_HELP);
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                final String line;
                System.out.print("> ");
                try {
                    line = scanner.nextLine().trim();
                } catch (final NoSuchElementException ex) { // EOF
                    break;
                }
                if (line.startsWith("#")) {
                    System.out.println(line);
                } else if (line.equalsIgnoreCase("exit")) {
                    break;
                } else if (!line.isEmpty()) {
                    handle(line.split("\\s+"));
                }
            }
        }
        System.out.println("Bye!");
    }

    private void listSensors(String... args) throws Exception {
        validateArgc(args, 0);

        final List<Sensor> sensors = conn.querySensors().get();
        Logger.clientResponseLS(sensors);
        //sensors.forEach(Sensor::printToStdOut); // abgestellt, weil unten prettyPrint
        printPrettyPrintSensorList(sensors);
    }

    private void queryData(String... args) throws Exception {
        validateArgc(args, 4, 6);
        final int sensorId = Integer.parseUnsignedInt(args[0]);
        final String type = args[1];
        final LocalDateTime from = Util.stringToLocalDateTime(args[2]);
        final LocalDateTime to = Util.stringToLocalDateTime(args[3]);
        if (args.length == 5) {
            if (!args[4].toLowerCase().matches("min|max|mean|median")) {
                CommandHandler.interfaceErr("Command invalid! Cannot use interval without indicating an operation!");
                return;
            }
        }
        final DataSeries.Operation operation = args.length < 5 ? DataSeries.Operation.NONE : DataSeries.Operation.valueOf(args[4].toUpperCase());
        final long interval = args.length < 6 ? from.until(to, ChronoUnit.SECONDS) : Math.min(Util.stringToInterval(args[5]), from.until(to, ChronoUnit.SECONDS));

        final DataQueryParameters dataQueryParameters = new DataQueryParameters(sensorId, type, from, to, operation, interval);

        final DataSeries series = conn.queryData(dataQueryParameters).get();
        CommandHandler.interfaceInfo(series.size() + " Datapoints received");

        if(series.size() == 0) {
            // if something doesn't work an empty series is returned. Type of Sensor contains error message as string
            Logger.err(series.getSensor().getType());
            CommandHandler.interfaceErr(series.getSensor().getType());
            return;
        }

        CommandHandler.interfacePrintQueryParams(dataQueryParameters);
        getFormattedOperationOutput(series);
    }

    private void queryLineChart(String... args) throws Exception {
        validateArgc(args, 5, 7);
        final int sensorId = Integer.parseUnsignedInt(args[0]);
        final String type = args[1];
        final LocalDateTime from = Util.stringToLocalDateTime(args[2]);
        final LocalDateTime to = Util.stringToLocalDateTime(args[3]);
        final String imagePath = args[4];
        final DataSeries.Operation operation = args.length < 6 ? DataSeries.Operation.NONE : DataSeries.Operation.valueOf(args[5].toUpperCase());
        final long interval = args.length < 7 ? from.until(to, ChronoUnit.SECONDS) : Math.min(Util.stringToInterval(args[6]), from.until(to, ChronoUnit.SECONDS));

        final DataQueryParameters dataQueryParameters = new DataQueryParameters(sensorId, type, from, to, operation, interval);
        final DataQueryParameters dataQueryMinParameters = new DataQueryParameters(sensorId, type, from, to, DataSeries.Operation.MIN, interval);
        final DataQueryParameters dataQueryMaxParameters = new DataQueryParameters(sensorId, type, from, to, DataSeries.Operation.MAX, interval);
        final DataQueryParameters dataQueryMeanParameters = new DataQueryParameters(sensorId, type, from, to, DataSeries.Operation.MEAN, interval);

        final DataSeries dataSeries = conn.queryData(dataQueryParameters).get();
        final DataPoint min = conn.queryData(dataQueryMinParameters).get().first();
        final DataPoint max = conn.queryData(dataQueryMaxParameters).get().first();
        final DataPoint mean = conn.queryData(dataQueryMeanParameters).get().first();

        LineChart lineChart = new LineChart();
        lineChart.initDiagram();
        lineChart.drawChart(dataSeries, min, max, mean);
        lineChart.save(imagePath);
        Logger.clientCreateLinechartImage(imagePath, dataSeries, min, max, mean);
    }

    private void queryScatterplot(String... args) throws Exception {
        validateArgc(args, 5, 7);
        final int sensorIdA = Integer.parseUnsignedInt(args[0]);
        final String metricA = args[1];
        final int sensorIdB = Integer.parseUnsignedInt(args[2]);
        final String metricB = args[3];
        final LocalDateTime from = Util.stringToLocalDateTime(args[4]);
        final LocalDateTime to = Util.stringToLocalDateTime(args[5]);
        final String imagePath = args[6];
        final DataSeries.Operation operation = args.length < 8 ? DataSeries.Operation.NONE : DataSeries.Operation.valueOf(args[7].toUpperCase());
        final long interval = args.length < 9 ? from.until(to, ChronoUnit.SECONDS) : Math.min(Util.stringToInterval(args[8]), from.until(to, ChronoUnit.SECONDS));

        final DataQueryParameters dataQueryParametersA = new DataQueryParameters(sensorIdA, metricA, from, to, operation, interval);
        final DataQueryParameters dataQueryParametersB = new DataQueryParameters(sensorIdB, metricB, from, to, operation, interval);
        final DataQueryParameters dataQueryMinParametersA = new DataQueryParameters(sensorIdA, metricA, from, to, DataSeries.Operation.MIN, interval);
        final DataQueryParameters dataQueryMaxParametersA = new DataQueryParameters(sensorIdA, metricA, from, to, DataSeries.Operation.MAX, interval);
        final DataQueryParameters dataQueryMinParametersB = new DataQueryParameters(sensorIdB, metricB, from, to, DataSeries.Operation.MIN, interval);
        final DataQueryParameters dataQueryMaxParametersB = new DataQueryParameters(sensorIdB, metricB, from, to, DataSeries.Operation.MAX, interval);

        final DataSeries dataSeriesA = conn.queryData(dataQueryParametersA).get();
        final DataSeries dataSeriesB = conn.queryData(dataQueryParametersB).get();

        if(dataSeriesA.size() != dataSeriesB.size() || dataSeriesA.isEmpty() || dataSeriesB.size() == 0) {
            Logger.err("Scatterplot can't be created");
            return;
        }

        final DataPoint minA = conn.queryData(dataQueryMinParametersA).get().first();
        final DataPoint maxA = conn.queryData(dataQueryMaxParametersA).get().first();
        final DataPoint minB = conn.queryData(dataQueryMinParametersB).get().first();
        final DataPoint maxB = conn.queryData(dataQueryMaxParametersB).get().first();

        ScatterPlot scatterplot = new ScatterPlot();
        scatterplot.initDiagram();
        scatterplot.drawChart(dataSeriesA, dataSeriesB, minA, maxA, minB, maxB);
        scatterplot.save(imagePath);
        Logger.clientCreateScatterplotImage(imagePath, dataSeriesA, dataSeriesB);
    }

    private void queryCluster(String... args) throws Exception {
        validateArgc(args, 14, 14);

        if (!args[5].toLowerCase().matches("min|max|mean|median")) {
            CommandHandler.interfaceErr("Command invalid! Cannot use interval without indicating an operation!");
            return;
        }
        List<Integer> sensorIds = new ArrayList<>();
        if (!args[0].toLowerCase().matches("all")){
            sensorIds = Stream.of(args[0].split(",")).map(Integer::parseUnsignedInt).collect(Collectors.toList());
        } else {
            sensorIds.add(-1);
        }
        final String metric = args[1];
        final LocalDateTime from = Util.stringToLocalDateTime(args[2]);
        final LocalDateTime to = Util.stringToLocalDateTime(args[3]);
        final long interval = Math.min(Util.stringToInterval(args[4]), from.until(to, ChronoUnit.SECONDS));
        final DataSeries.Operation operation = DataSeries.Operation.valueOf(args[5].toUpperCase());
        final int length = Integer.parseUnsignedInt(args[6]);
        final int gridHeight = Integer.parseUnsignedInt(args[7]);
        final int gridWidth = Integer.parseUnsignedInt(args[8]);
        final Double updateRadius = Double.parseDouble(args[9]);
        final Double learningRate = Double.parseDouble(args[10]);
        final Long iterationPerCurve = Long.parseUnsignedLong(args[11]);
        final int resultID = Integer.decode(args[12]);
        final int amountOfIntermediateResults = Integer.parseUnsignedInt(args[13]);


        SOMQueryParameters somQueryParameters = new SOMQueryParameters(sensorIds, metric, from, to, operation, interval, length,
                gridHeight, gridWidth, updateRadius, learningRate, iterationPerCurve, resultID, amountOfIntermediateResults);
        CommandHandler.interfacePrintSOMQueryParams(somQueryParameters);

        final List<ClusterDescriptor> clusterDescriptorList = conn.queryCluster(somQueryParameters).get();
        if(!clusterDescriptorList.isEmpty() && !clusterDescriptorList.get(0).getMembers().isEmpty()) {
            if (clusterDescriptorList.get(0).getMembers().get(0).getSensor().getType().contains("ERROR")) {
                interfaceErr(clusterDescriptorList.get(0).getMembers().get(0).getSensor().getType());
                return;
            }
        }
        Logger.clientResponseCluster(somQueryParameters);
        interfaceInfo("Cluster Query finished");
        // TODO CommandHandler.interfacePrintClusterDescriptor(clusterDescriptorlist); for cD in cD-list print?
    }

    private void listFinishedClusteringResults(String... args) throws Exception {
        validateArgc(args, 0);
        boolean results_found = false;

        Logger.clientListResults();
        interfaceResult("List of results found: ");

        String filePath = "clusteringResults/";
        File folder = new File(filePath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String foldername = file.getName();
                File[] foldercontents = file.listFiles();
                for (File contentFile : foldercontents) {
                    String filename = contentFile.getName();
                    if (filename.endsWith("_final.json")) {
                        int folderID = Integer.parseInt(foldername);
                        System.out.println("         " + "* 0x" + String.format("%x", folderID).toUpperCase());
                        results_found = true;
                    }
                }
                if (!results_found) {
                    interfaceInfo("--> There was nothing with _final but there were intermediate results.");
                }
            }
        } else {
            interfaceErr("\n         " + "No finished results were found.");
        }
    }

    private void removeClusteringQueryResult(String... args) throws Exception {
        validateArgc(args, 1, 1);
        int resultID;
        if(args[0].contains("0x"))
            resultID = Integer.parseUnsignedInt(args[0].substring(2), 16);
        else
            resultID = Integer.parseUnsignedInt(args[0]);
        String filePath = "clusteringResults/" + Integer.toString(resultID);
        File folder = new File(filePath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        else {
            interfaceErr("Clustering result doesn't exist");
            return;
        }
        folder.delete();
        String hexName = String.format("%x", resultID).toUpperCase();
        interfaceInfo("deleted clustering result with ID: 0x" + hexName);

        Logger.clientRemoveResult(resultID);
    }

    private void inspectCluster(String... args) throws Exception {
        validateArgc(args, 4, 4);
        int resultId;
        if(args[0].contains("0x"))
            resultId = Integer.parseUnsignedInt(args[0].substring(2), 16);
        else
            resultId = Integer.parseUnsignedInt(args[0]);
        final int heightIndex = Integer.parseUnsignedInt(args[1]);
        final int widthIndex = Integer.parseUnsignedInt(args[2]);
        boolean verbose = Boolean.parseBoolean(args[3]);

        boolean result_found = false;
        boolean heightWidthExists = false;

        String filePath = "clusteringResults/" + Integer.toString(resultId);
        String fileName = Integer.toString(resultId) + "_final.json";
        String fullPathToFile = "clusteringResults/" + Integer.toString(resultId) + "/" + Integer.toString(resultId) + "_final.json";

        File folder = new File(filePath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith("_final.json")) {
                    result_found = true;
                    try {
                        Gson gson = new Gson();
                        Type type = new TypeToken<Collection<ClusterDescriptor>>(){}.getType();
                        List<ClusterDescriptor> clusteringResults = gson.fromJson(new FileReader(fullPathToFile), type);

                        ClusterDescriptor searchedClusterDescriptor = null;
                        for(ClusterDescriptor clusterIterator : clusteringResults) {
                            if(clusterIterator.getHeigthIndex() == heightIndex && clusterIterator.getWidthIndex() == widthIndex) {
                                searchedClusterDescriptor = clusterIterator;
                                heightWidthExists = true;
                                break;
                            }
                            //System.out.println("clusterIterator Height|Width" + clusterIterator.getHeigthIndex() + " | " + clusterIterator.getWidthIndex() + " | " + clusterIterator.getMembers().size());
                        }
                        if (!heightWidthExists) {
                            interfaceErr("Requested indices don't exist.");
                            throw new CommandException("Requested indices don't exist. " + MSG_HELP);
                        }

                        StringBuilder tableHeading = new StringBuilder();
                        tableHeading.append("\n         ");
                        tableHeading.append("| ----------------------------------- |");
                        tableHeading.append("\n         ");
                        tableHeading.append("| Node (" + heightIndex + ", " + widthIndex + ") from " + resultId + "              ");

                        interfaceInfo(tableHeading.toString());

                        CommandHandler.interfacePrintClusterDescriptor(searchedClusterDescriptor);
                        if (verbose) {
                            //System.out.println("VERBOSE!");
                            //CommandHandler.interfacePrintClusterDescriptorMembers(searchedClusterDescriptor);
                        }
                        //Logger.clientInspectCluster(resultId, heightIndex, widthIndex, clusteringResults[1]);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        if (!result_found) {
            interfaceErr("Clustering result doesn't exist");
        }

    }

    private void displayHelp(String... args) {
        System.out.println("Usage:");
        System.out.println("  ls\t- Lists all sensors and metrics.");
        System.out.println("  data <sensorId> <metric> <from-time> <to-time> [operation [interval<s|m|h|d>]]\t- Displays historic values measured by a sensor.");
        System.out.println("  linechart <sensorId> <metric> <from-time> <to-time> [operation [interval<s|m|h|d>]]\t- Creates a Linechart png with values measured by a sensor.");
        System.out.println("  scatterplot <sensorId1> <metric1> <sensorId2> <metric2> <from-time> <to-time> [operation [interval<s|m|h|d>]]\t- Creates a Scatterplot png with values measured by two sensors.");
        System.out.println("  exit\t- Terminate the CLI.");
        System.out.println("More information is contained in the assignment description and in the folder queries/.");
        System.out.println();
    }

    private static void getFormattedOperationOutput(DataSeries data) {
        StringBuilder content = new StringBuilder();
        content.append("This is the result of the requested operation: ");
        content.append("\n         ");
        content.append("| --------------------------------------- |");
        content.append("\n         ");
        content.append("|          Time          |");
        switch (data.getOperation()) {
            case NONE:
                content.append("     value      |");
                break;
            case MIN:
                content.append("      MIN       |");
                break;
            case MAX:
                content.append("      MAX       |");
                break;
            case MEAN:
                content.append("     MEAN       |");
                break;
            case MEDIAN:
                content.append("     MEDIAN     |");
                break;
            default:
                content.append("     value       |");
        }
        content.append("\n         ");
        content.append("| --------------------------------------- |");
        data.forEach((DataPoint x) -> {
            content.append("\n         ");
            content.append(String.format("|  %20s ", x.getTime().toString()));
            content.append(" | ");
            content.append(String.format("%13.3f ", x.getValue()));
            content.append(" |");
        });
        content.append("\n         ");
        content.append("| --------------------------------------- |");
        interfaceResult(content.toString());
    }

    private static void printPrettyPrintSensorList(List<Sensor> data) {
        List<Sensor> sorted_data = new ArrayList<Sensor>(data);
        sorted_data.sort((Sensor a, Sensor b) -> {
            if (a.getLocation().compareTo(b.getLocation()) == 0) {
                return a.getMetric().compareTo(b.getMetric());
            } else {
                return a.getLocation().compareTo(b.getLocation());
            }
        });
        StringBuilder content = new StringBuilder();
        content.append("\n         ");
        content.append("| ------------------------------------------------------------------------ |");
        content.append("\n         ");
        content.append("| SENSOR-ID |    TYPE    |   LAT   |   LON   |    LOC    |      METRIC     |");
        content.append("\n         ");
        content.append("| ------------------------------------------------------------------------ |");
        content.append("\n         ");
        content.append("| ------------------------------------------------------------------------ |");
        sorted_data.forEach((Sensor sensor) -> {
            content.append("\n         ");
            content.append(String.format("|      %-4d ", sensor.getId()));
            content.append(String.format("|  %9s ", sensor.getType()));
            content.append(String.format("|  %6.3f ", sensor.getLatitude()));
            content.append(String.format("|  %6.3f ", sensor.getLongitude()));
            content.append(String.format("|  %8s ", sensor.getLocation()));
            content.append(String.format("|  %13s ", sensor.getMetric()));
            content.append(" |");
        });
        content.append("\n         ");
        content.append("| ------------------------------------------------------------------------ |");
        interfaceResult(content.toString());
    }

    public static void interfacePrintQueryParams(DataQueryParameters params) {
        StringBuilder content = new StringBuilder();
        content.append("Those were the parameters of the requested query: ");
        content.append("\n         ");
        content.append("| -------------------------------------------------------------------------------------------------------------- |");
        content.append("\n         ");
        content.append("|  ID of SENSOR  |    METRIC    |           FROM           |            TO            |  OPERATION  |  INTERVAL  |");
        content.append("\n         ");
        content.append("| -------------------------------------------------------------------------------------------------------------- |");
        content.append("\n         ");
        content.append(String.format("|          %-4d  ", params.getSensorId()));
        content.append(String.format("|  %11s ", params.getMetric()));
        content.append(String.format("|    %20s  ", params.getFrom().toString()));
        //content.append(String.format("%2d", params.getFrom().toString().length()) );
        content.append(String.format("|    %20s  ", params.getTo().toString()));
        content.append(String.format("| %10s  ", params.getOperation()));
        content.append(String.format("|  %-8d ", params.getInterval()));
        content.append(" |");
        content.append("\n         ");
        content.append("| -------------------------------------------------------------------------------------------------------------- |");
        content.append("\n         ");

        interfaceInfo(content.toString());
    }
    public static void interfacePrintSOMQueryParams(SOMQueryParameters somQueryParams) {
        StringBuilder content = new StringBuilder();
        content.append("Those were the parameters of the requested SOM query: ");
        content.append("\n         ");
        content.append("\n         ");
        content.append("| -------------------------------------------------------------------------------------------- |");
        content.append("\n         ");
        if(somQueryParams.getSensorIds().size() == 1)
            content.append("| sensors:    \"all\"");
        else
            content.append("| sensors:    \"" + somQueryParams.getSensorIds().toString() + "\"");
        content.append("\n         ");
        content.append("| type:       \"" + somQueryParams.getMetric() + "\"");
        content.append("\n         ");
        content.append("| from:       \"" + somQueryParams.getFrom() + "\" until \"" + somQueryParams.getTo() + "\"");
        content.append("\n         ");
        content.append("| packing:     "  + somQueryParams.getLength() +
                " DataPoints into one curve, using " + somQueryParams.getOperation() +
                " with a sampling size of " + somQueryParams.getInterval() + " seconds");
        content.append("\n         ");
        content.append("| SOM-params: \"(" + somQueryParams.getGridHeight() + ", " +
                somQueryParams.getGridWidth() + ") - Grid with a learning rate of "
                + somQueryParams.getLearningRate() + " \n         |              and an initial update Radius of " +
                somQueryParams.getUpdateRadius() + " times\n         |              the diameter of the grid");
        content.append("\n         ");
        content.append("|              "  + "for " + somQueryParams.getIterationsPerCurve() + " iterations per curve.\"");
        content.append("\n         ");
        content.append("| ResultID:   \"" + String.format("0x%x", somQueryParams.getResultId()).toUpperCase() +
                "\" will contain " + somQueryParams.getAmountOfIntermediateResults() + " intermediate results.");

        content.append("\n         ");
        content.append("| -------------------------------------------------------------------------------------------- |");

        interfaceInfo(content.toString());
    }

    public static void interfacePrintClusterDescriptor(ClusterDescriptor cD) {
        StringBuilder content = new StringBuilder();
        content.append("         ");
        content.append("| ----------------------------------- |");
        content.append("\n         ");
        content.append("| #Members    | " + String.format("%6d (%1.3f)", cD.getMembers().size(), cD.getNormalizedAmountOfMembers()));
        content.append("\n         ");
        content.append("| ----------------------------------- |");
        content.append("\n         ");
        content.append("| #Error      | " + String.format("%5.3f (%1.3f)", cD.getError(), cD.getNormalizedError()));
        content.append("\n         ");
        content.append("| ----------------------------------- |");
        content.append("\n         ");
        content.append("| #Entropy    | " + String.format("%5.3f (%1.3f)", cD.getDistanceEntropy(), cD.getNormalizedDistanceEntropy()));
        content.append("\n         ");
        content.append("| ----------------------------------- |");
        System.out.println(content.toString());
    }
    public static void interfacePrintClusterDescriptorMembers(ClusterDescriptor clusterDescriptor) {
        StringBuilder content = new StringBuilder();
        content.append("\n         ");
        content.append("| List of all members:                |");
        content.append("\n         ");
        content.append("| ----------------------------------- |");
        for (DataSeries dataSeries : clusterDescriptor.getMembers()) { // TODO error cannot cast treeset to Dataseries but (List<DataSeries>) sei redundant
            // TODO if members - nicht alle haben members
            // er ließt das nur aus dem JSOn ein und die Sensoren-Info ist dort net.. d.h. es wird er Value wohl gewünscht...
            Sensor sensor = dataSeries.getSensor();
            int sensorID = sensor.getId();
            LocalDateTime from = dataSeries.first().getTime();
            LocalDateTime to = dataSeries.last().getTime();
            // TODO das JSON hat hier nur einen Value und ein Time... aber das hat ClusterDescriptor nicht?
            // List<DataSeries> members | DataSeries extends TreeSet<DataPoint> | hat Time und Value..
            // das JSON hat Time und Value direkt als DataSeries drin.. wie kriege ich die Points da wieder raus??
            // Cache geht nicht, weil ich bin am Client
            content.append("\n         ");
            content.append(String.format("|          %-4d  ", sensorID));
            content.append(String.format("|    %20s  ", from.toString()));
            content.append(String.format("|    %20s  ", to.toString()));
            content.append(" |");
        }
        content.append("\n         ");
        content.append("| ----------------------------------- |\n");
        System.out.println(content.toString());
    }

    public static void interfaceInfo(String msg) {
        Logger.info(msg);
        System.out.println(LocalDateTime.now().toString() + " " + ANSI_GREEN + "[ INFO   ] " + ANSI_RESET + msg);
    }

    public static void interfaceResult(String msg) {
        System.out.println(LocalDateTime.now().toString() + " " + ANSI_YELLOW + "[ RESULT ] " + ANSI_RESET + msg);
    }

    public static void interfaceErr(String msg) {
        Logger.err(msg);
        System.out.println(LocalDateTime.now().toString() + " " + ANSI_RED + "[ ERROR  ] " + ANSI_RESET + msg);
    }

    public static void interfaceWarn(String msg) {
        Logger.warn(msg);
        System.out.println(LocalDateTime.now().toString() + " " + ANSI_YELLOW + "[ WARNING] " + ANSI_RESET + msg);
    }

    @FunctionalInterface
    private interface Command {
        void handle(String... args) throws Exception;
    }

    private static final class CommandException extends Exception {
        public CommandException(String message) {
            super(message);
        }
    }
}
