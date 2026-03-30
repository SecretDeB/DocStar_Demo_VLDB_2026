package com.docstar;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.docstar.model.dto.ClientRequest;
import com.docstar.model.dto.DBORequest;

import lombok.Data;

@Data
public class ServerDispatcher {

    // ============ SHARED DATA (Loaded Once) ============
    public BigInteger[][] act;
    public long[][] files;
    public long[][] addr;
    public long[] optInv;
    public long[][] fileKeyword;
    public long[][] optInvHashValues;
    public long[] optInvHashValuesFlattened;
    public boolean initialized = false;
    private int seedServer;

    public int keywordCount;
    public int serverCount;
    public int fileCount;
    public int fileLength;
    public int hashBlockSize = Constant.getHashBlockSize();
    public int hashBlockCount = Constant.getHashBlockCount();
    public int checkpoint1 = Constant.getCheckpoint1();
    public int checkpoint2 = Constant.getCheckpoint2();
    public long modValue = Constant.getModParameter();
    public final BigInteger modValueBig = Constant.getModBigParameter();
    public Set<String> cleartextKeywords;
    public int optCol;
    public int optRow;
    public int clientCount;
    public Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ServerDispatcher(int serverNumber) {
        this.serverNumber = serverNumber;
        // Load all data structures ONCE
        loadDataStructures();

        startListener();

        // Initialize server connections
        initializeServerConnections();
    }

    public int serverNumber;
    public int serverPort;
    public Path basePath = Paths.get(System.getProperty("user.dir") + "/documentms/src/main/resources/");

    // ============ CONNECTION MANAGEMENT ============
    public final ExecutorService searchExecutor = Executors.newFixedThreadPool(10);
    public final Map<String, ServerMultiParallel> activeSearches = new ConcurrentHashMap<>();

    // Server connections for inter-server communication
    public Map<Integer, ServerConnection> serverConnections = new HashMap<>();

    public final Map<String, BlockingQueue<PeerMessage>> sessionQueues = new ConcurrentHashMap<>();
    public Properties commonProperties = new Properties();
    public final BlockingQueue<DBORequest> dboQueue = new LinkedBlockingQueue<>();

    public BlockingQueue<PeerMessage> registerSession(String sessionId) {
        BlockingQueue<PeerMessage> queue = new LinkedBlockingQueue<>();
        sessionQueues.put(sessionId, queue);
        return queue;
    }

    public void routeMessageToSession(PeerMessage message) {
        BlockingQueue<PeerMessage> queue = sessionQueues.get(message.getSessionId());
        if (queue != null) {
            queue.offer(message);
        }
    }

    public void initializeServerConnections() {
        System.out.println("Server " + serverNumber + " initializing connections...");

        // Give other servers time to start their listeners
        try {
            Thread.sleep(2000 * serverNumber); // Stagger based on server number
        } catch (InterruptedException e) {
        }

        for (int targetServer = 1; targetServer <= 4; targetServer++) {
            if (targetServer != serverNumber) {
                connectToServer(targetServer);
            }
        }

        System.out.println("Server " + serverNumber + " connected to " + serverConnections.size() + " servers");
    }

    public void connectToServer(int targetServer) {
        int retryDelay = 5000; // 2 seconds

        String serverIP = commonProperties.getProperty("serverIP" + targetServer);
        int serverPort = Integer.parseInt(commonProperties.getProperty("serverPort" + targetServer));

        while (true) {
            try {
                System.out.println("Server IP:" + serverIP + " Server Port:" + serverPort);

                System.out.println("Server " + serverNumber + " attempting connection to Server " + targetServer);

                ServerConnection conn = new ServerConnection(targetServer, serverIP, serverPort);
                serverConnections.put(targetServer, conn);

                System.out.println("✓ Server " + serverNumber + " connected to Server " + targetServer);
                return; // Success, exit method

            } catch (Exception e) {
                System.err.println("✗ Server " + serverNumber + " failed to connect to Server " + targetServer +
                        ": " + e.getMessage());

                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                }

            }
        }
    }

    public BigInteger[][] loadACTFromFile() {
        String actName = "act" + (Math.floorMod(serverNumber - 2, serverCount) + 1) + ".txt";
        Path actPath = basePath.resolve("data/share").resolve(actName);
        File filetoRead = new File(actPath.toString());
        if (filetoRead.exists()) {
            try {
                act = Helper.readFileAsBigInt(actPath.toFile());
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            act = new BigInteger[2][0];
        }
        return act;
    }

    public long[][] loadADDRFromFile() {
        // ADDR
        String addrName = "addr" + (Math.floorMod(serverNumber - 2, serverCount) + 1) + ".txt";
        Path addrPath = basePath.resolve("data/share").resolve(addrName);
        File filetoRead = new File(addrPath.toString());
        if (filetoRead.exists()) {
            try {
                addr = Helper.readFileAsLong(addrPath.toFile());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            addr = new long[0][];
        }
        return addr;
    }

    public long[] loadOptInvFromFile() {
        // Inverted index
        String optInvName = "invertedIndexOpt" + serverNumber + ".txt";
        Path optInvPath = basePath.resolve("data/share").resolve(optInvName);
        File filetoRead = new File(optInvPath.toString());
        if (filetoRead.exists()) {
            try {
                optInv = Helper.readFileAs1D(optInvPath.toFile());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            optInv = new long[0];
        }
        return optInv;
    }

    public long[][] loadFilesFromFile() {
        // Files
        String fileName = "file" + (Math.floorMod(serverNumber - 2, serverCount) + 1) + ".txt";
        Path filesPath = basePath.resolve("data/share").resolve(fileName);
        File filetoRead = new File(filesPath.toString());
        if (filetoRead.exists()) {
            try {
                files = Helper.readFileAsLong(filesPath.toFile());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            files = new long[0][];
        }
        return files;
    }

    public long[][] loadFileKeywordFromFile() {
        // File keywords
        String fileKeywordName = "fileKeyword" + (Math.floorMod(serverNumber - 2, serverCount) + 1) + ".txt";
        Path fileKeywordPath = basePath.resolve("data/share").resolve(fileKeywordName);
        File filetoRead = new File(fileKeywordPath.toString());
        if (filetoRead.exists()) {
            try {
                fileKeyword = Helper.readFileAsLong(fileKeywordPath.toFile());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            fileKeyword = new long[1][];
            // fileKeyword[0] = new long[Constant.getHashBlockCount() + checkpoint1];
        }
        return fileKeyword;
    }

    public void loadDataStructures() {
        System.out.println("Loading shared data structures...");

        this.serverCount = 4;

        // Load ACT
        this.act = loadACTFromFile();

        // Load Files
        this.files = loadFilesFromFile();

        // Load ADDR
        this.addr = loadADDRFromFile();

        // Load OptInv
        this.optInv = loadOptInvFromFile();

        // Load FileKeyword
        this.fileKeyword = loadFileKeywordFromFile();

        this.keywordCount = this.act[0].length - 2;
        this.fileCount = this.files.length;

        System.out.println("Base path: " + basePath.toString());
        try (InputStream input = ServerDispatcher.class.getClassLoader().getResourceAsStream("Common.properties")) {
            if (input == null) {
                System.err.println("❌ ERROR: common.properties not found in the classpath.");
                // You may want to throw an exception here
                return;
            }
            // Load the properties from the InputStream
            commonProperties.load(input);
            System.out.println("✅ Properties loaded successfully.");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.serverPort = Integer
                .parseInt(commonProperties.getProperty("serverPort" + serverNumber));
        this.fileLength = Integer
                .parseInt(commonProperties.getProperty("maxFileLength"));
        this.seedServer = Integer.parseInt(commonProperties.getProperty("seedServer" + serverNumber));

        System.out.println("Data loaded: " + keywordCount + " keywords, " + fileCount + " files");
    }

    public void startListener() {
        Thread listenerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                System.out.println("Server " + serverNumber + " listening on port " + (serverPort));

                while (true) {
                    Socket incomingSocket = serverSocket.accept();
                    // Handle incoming peer connection
                    handleIncomingConnection(incomingSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void handleIncomingConnection(Socket incomingSocket) {
        new Thread(() -> {

            try {
                ObjectInputStream in = new ObjectInputStream(incomingSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(incomingSocket.getOutputStream());
                while (true) {
                    try {
                        Object obj = in.readObject();
                        if (obj instanceof PeerMessage) {
                            PeerMessage message = (PeerMessage) obj;
                            // Route message to appropriate session
                            routeMessageToSession(message);
                        } else if (obj instanceof DBORequest) {
                            DBORequest request = (DBORequest) obj;
                            // Enqueue the DBO request for processing
                            request.setDboObjectOutputStream(out);
                            dboQueue.offer(request);
                        } else if (obj instanceof ClientRequest) {
                            ClientRequest clientRequest = (ClientRequest) obj;
                            // Handle client request
                            // Here you would process the client request accordingly
                            String sessionId = clientRequest.getSessionId();

                            if (activeSearches.containsKey(sessionId)) {
                                System.out.println("Adding request to existing search session: " + sessionId);
                                activeSearches.get(sessionId).getClientRequestQueue().offer(clientRequest);
                            } else {
                                ServerMultiParallel serverMulti = new ServerMultiParallel(sessionId, this.serverNumber,
                                        this);
                                serverMulti.setClientOutputStream(out);
                                serverMulti.initSearch((Boolean) clientRequest.getPayload());
                                activeSearches.put(sessionId, serverMulti);

                                CompletableFuture.runAsync(() -> {
                                    serverMulti.executeSearch();
                                }, searchExecutor);
                            }

                        } else {
                            System.err.println("Received unknown object type from peer");
                        }

                    } catch (EOFException e) {
                        // Client closed connection normally
                        break;
                    } catch (SocketException e) {
                        // Connection reset
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // System.out.println("\n\nPeer connection closed");
            }
        }).start();

    }

    public ServerConnection getServerConnection(int serverNum) {
        return serverConnections.get(serverNum);
    }

    public static void main(String[] args) {

        System.out.println("Starting Server Dispatcher..." + args[0]);

        int serverNumber = Integer
                .parseInt(args[0]);

        ServerDispatcher dispatcher = new ServerDispatcher(serverNumber);

        System.out.println("Server Stats: " +
                " Server Number: " + dispatcher.getServerNumber() +
                " Server Port: " + dispatcher.getServerPort());

        // Keep server running
        System.out.println("✓ Server " + serverNumber + " ready and connected!");
        DBOServerParallel dboServer = new DBOServerParallel(dispatcher, dispatcher.commonProperties);
        Runnable dboWorker = () -> {
            while (true) {
                try {
                    // A. Wait for a command
                    DBORequest request = dispatcher.dboQueue.take();
                    String operation = request.getPayload().toString(); // Assuming payload is the integer code
                    System.out.println("Received an operation " + operation);
                    dboServer.setDboObjectOutputStream(request.getDboObjectOutputStream());
                    // B. Acquire exclusive WRITE lock (all searches will pause)
                    // dispatcher.lock.writeLock().lock();
                    try {
                        System.out.println("DBO WORKER: Starting operation " + operation);
                        switch (operation) {
                            case "GET_STATS":
                                dboServer.getStats();
                                break;
                            case "ADD_FILE_CONTENT": // Add File
                                dboServer.addFileContent();
                                break;
                            case "ADD_FILE_TO_KEYWORD": // Add File
                                dboServer.addFilesOpt();
                                break;
                            case "ADD_KEYWORD": // Add Keyword
                                dboServer.addNewKeyword();
                                break;
                            case "ADD_CLIENT": // Add New Client
                                dboServer.addNewClient();
                                break;
                            case "ADD_KEYWORDS_BATCH": // Add Keyword
                                dboServer.addNewKeywordsBatch();
                                break;
                            case "DELETE_KEYWORD": // Delete keyword
                                dboServer.deleteKeywordOpt();
                                break;
                            case "DELETE_FILE": // Delete file from keyword
                                dboServer.deleteFileOpt();
                                break;
                            case "REVOKE_GRANT_ACCESS": // Revoke or Grant Access on keyword
                                dboServer.revokeGrantAccess();
                                break;
                            case "REVOKE_GRANT_ACCESS_BATCH": // Revoke or Grant Access on keyword
                                dboServer.revokeGrantAccessBatch();
                                break;
                            case "GET_KEYWORDS": // Get all the keywords
                                dboServer.getAllKeywords();
                                break;
                            case "GET_KEYWORD_INDEX":
                                dboServer.getKeywordIndex();
                                break;
                            case "BATCH_ADD_FILE":
                                dboServer.batchAddFileToKeywords();
                                break;
                            case "GET_ACCESS":
                                dboServer.getAccessKeyword();
                            case "GET_ALL_CLIENT_ACCESS":
                                dboServer.getAllClientAccess();
                                break;
                            case "GET_FILE_COUNT": // Fetch File Count
                                dboServer.getFileCount();
                                break;
                            case "GET_FILE": // Fetch file
                                dboServer.getFileShare();
                                break;
                            case "GET_CLIENT_COUNT": // Fetch Client Count
                                dboServer.getClientCount();
                                break;
                            case "GET_KEYWORD_COUNT": // Fetch Client Count
                                dboServer.getKeywordCount();
                                break;
                            case "BULK_INITIALIZE":
                                dboServer.bulkInitializeSystem();
                                break;
                            case "BULK_UPLOAD_FILES":
                                dboServer.bulkUploadFiles();
                                break;
                            case "GET_ALL_FILES":
                                dboServer.getAllFiles();
                                break;
                            case "GET_OPT_DIMENSIONS":
                                dboServer.sendOptDimensions();
                                break;
                            case "RESET_SERVER":
                                dboServer.resetServer();
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // C. ALWAYS release the lock
                        // server.lock.writeLock().unlock();
                        System.out.println("DBO WORKER: Operation complete. Released write lock.");
                    }
                } catch (Exception e) {
                    dispatcher.log.log(Level.SEVERE, "Error in DBO worker thread", e);
                }
            }
        };
        new Thread(dboWorker, "DBOWorkerThread").start();
        System.out.println("SERVER: DBO Worker started.");
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}