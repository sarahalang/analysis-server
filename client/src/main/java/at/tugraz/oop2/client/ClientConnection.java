package at.tugraz.oop2.client;

import at.tugraz.oop2.Logger;
import at.tugraz.oop2.data.*;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Used for managing the connection to the server and for sending requests.
 */
public final class ClientConnection implements AutoCloseable {
    private final LinkedBlockingQueue<ConnectionEventHandler> connectionClosedEventHandlers;
    private final LinkedBlockingQueue<ConnectionEventHandler> connectionOpenedEventHandlers;
    private Socket socket;
    private DataOutputStream dataOutputStream;
    private ObjectInputStream objectInputStream;

    public ClientConnection() {
        connectionClosedEventHandlers = new LinkedBlockingQueue<>();
        connectionOpenedEventHandlers = new LinkedBlockingQueue<>();
    }

    public void connect(String url, int port) throws IOException {
        socket = new Socket(url, port);
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        objectInputStream = new ObjectInputStream(inputStream);
        dataOutputStream = new DataOutputStream(outputStream);
        System.out.println("Connection established...");
    }

    @Override
    public void close() {
        connectionClosedEventHandlers.forEach(this::addConnectionClosedListener);
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("Couldn't close socket.");
            e.printStackTrace();
        }
    }

    /**
     * Registers a handler that will be called when the connection is opened.
     */
    public void addConnectionClosedListener(ConnectionEventHandler eventHandler) {
        connectionClosedEventHandlers.add(eventHandler);
    }

    /**
     * Registers a handler that will be called when the connection is closed either by
     * the client itself or by the server.
     */
    public void addConnectionOpenedListener(ConnectionEventHandler eventHandler) {
        connectionOpenedEventHandlers.add(eventHandler);
    }


    public CompletableFuture<List<Sensor>> querySensors() {
        String toSend = "ls";
        CompletableFuture<List<Sensor>> receivedList = new CompletableFuture<>();
        try {
            Logger.clientRequestLS();
            dataOutputStream.writeUTF(toSend);

            List<Sensor> sensors = (List<Sensor>) objectInputStream.readObject();

            Logger.clientResponseLS(sensors);
            Logger.info("List with " + sensors.size() + " Sensors received!");
            CommandHandler.interfaceInfo("List with " + sensors.size() + " Sensors received!");

            receivedList.complete(sensors);
        } catch (Exception e){
            System.out.println("Send Message failed");
        }
        return receivedList;
    }

    public CompletableFuture<DataSeries> queryData(DataQueryParameters dataQueryParameters) {
        CompletableFuture<DataSeries> receivedList = new CompletableFuture<>();
        try {
            Logger.clientRequestData(dataQueryParameters);
            String toSend = "data, " + dataQueryParameters.toString();
            dataOutputStream.writeUTF(toSend);

            DataSeries dataSeries = (DataSeries) objectInputStream.readObject();

            Logger.clientResponseData(dataQueryParameters, dataSeries);
            receivedList.complete(dataSeries);

        } catch (Exception e) {
            System.out.println("Query Data failed.");
        }
        return receivedList;
    }

    public CompletableFuture<List<ClusterDescriptor>> queryCluster(SOMQueryParameters somQueryParameters) {
        CompletableFuture<List<ClusterDescriptor>> receivedCluster = new CompletableFuture<>();
        try{
            Logger.clientRequestCluster(somQueryParameters);
            String toSend = "cluster, " + somQueryParameters.toString();
            dataOutputStream.writeUTF(toSend);

            List<ClusterDescriptor> clusterDescriptor = null;

             for (int interResult = 0; interResult < somQueryParameters.getAmountOfIntermediateResults(); interResult++) {
                clusterDescriptor = (List<ClusterDescriptor>) objectInputStream.readObject();
                if(clusterDescriptor.isEmpty() || (clusterDescriptor.size() == 1 && clusterDescriptor.get(0).getMembers().get(0).getSensor().getType().contains("ERROR"))) {
                    receivedCluster.complete(clusterDescriptor);
                    Logger.err("GETFUCKED");
                    return receivedCluster;
                } else if (clusterDescriptor.size() == 1 && clusterDescriptor.get(0).getMembers().get(0).getSensor().getType().contains("WARN")) {
                    CommandHandler.interfaceWarn(clusterDescriptor.get(0).getMembers().get(0).getSensor().getType());
                    interResult--;
                    continue;
                }
                Logger.clientIntermediateResponse(somQueryParameters, interResult);
                saveJson(Integer.toString(somQueryParameters.getResultId()), Integer.toString(interResult), clusterDescriptor);
             }
            clusterDescriptor = (List<ClusterDescriptor>) objectInputStream.readObject();
            saveJson(Integer.toString(somQueryParameters.getResultId()), "final", clusterDescriptor);

            receivedCluster.complete(clusterDescriptor);
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Query Cluster failed.");
        }

        return receivedCluster;
    }

    private void saveJson(String resultId, String fileEnding, List<ClusterDescriptor> clusterDescriptor) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String filePath = "clusteringResults/" + resultId + "/" + resultId +"_" + fileEnding + ".json";
        // System.out.println(gson.toJson(clusterDescriptor));
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        Writer writer = new FileWriter(file);
        gson.toJson(clusterDescriptor, writer);
        writer.close();
    }

    @FunctionalInterface
    public interface ConnectionEventHandler {
        void apply();
    }
}
