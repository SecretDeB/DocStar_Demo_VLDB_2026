package com.docstar.middleware;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

public class ServerConnection {
    @SuppressWarnings("unused")
    private final int serverNumber;
    private Socket socket;
    private ObjectOutputStream out;
    public ObjectInputStream in;
    private String ip;
    private int port;
    private final Object sendLock = new Object();

    public ServerConnection(int serverNumber, String ip, int port) throws IOException {
        this.serverNumber = serverNumber;
        connect(ip, port);
    }

    public void send(Object message) throws IOException {
        synchronized (sendLock) {
            while (true) {
                try {
                    out.writeObject(message);
                    out.flush();
                    break;
                } catch (SocketException e) {
                    // try to reconnect
                    try {
                        connect(ip, port);
                    } catch (IOException ce) {
                        // wait and try again
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    public Object receive() throws IOException, ClassNotFoundException {
        return in.readObject();
    }

    private void connect(String ip, int port) throws IOException {
        this.socket = new Socket(ip, port);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

}