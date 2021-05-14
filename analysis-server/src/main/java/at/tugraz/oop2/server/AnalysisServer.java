package at.tugraz.oop2.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.ObjectOutputStream;

/**
 * This class will hold the implementation of your server and handle connecting and connected clients.
 */
public final class AnalysisServer {
    private final int serverPort;
    private final String dataPath;
    private ServerSocket serverSocket;

    public AnalysisServer(int serverPort, String dataPath) {
        this.serverPort = serverPort;
        this.dataPath = dataPath;
    }

    public void run() {
        // TODO Start here with a loop accepting new client connections.
        try {
            serverSocket = new ServerSocket(serverPort);

            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//                System.out.println("new client connected...");

                Thread clienthandler = new ClientHandlerThread(socket, dataInputStream, objectOutputStream);
                clienthandler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

