package com.docstar;

import lombok.Data;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import com.docstar.model.dto.ClientRequest;

@Data
public class ServerMultiParallel {

    public final Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    // ============ INSTANCE VARIABLES (Per-Search) ============
    private String sessionId;
    private final int serverNumber;
    private final ServerDispatcher dispatcher; // Reference to data holder

    // Search-specific variables
    private String searchKeyword;
    private int clientId;
    private boolean clientVerification;
    private int serverCount;

    // Phase results
    private BigInteger searchKeywordShare;
    private long[] searchKeywordVectorShare;
    public StringBuilder[] phase1Result;
    private ArrayList<Long> phase2Result;
    private long[][] phase3Result;
    public long[] phase2AddrResult;
    public long[] phase2AddrResultSlice;

    // Temporary computation variables
    private long[] randomSharesPhase1Sum;
    public long[][] randomSharesPhase2;
    public long[] randomSharesPhase2Sum;
    private long[][] objectsReceivedLong1D;
    private long[][][] serverVerificationShares2D;
    // to stores server shares as long values for 1D array
    public String[][] objectsReceivedString1D;
    // to stores server shares as long values for 2D array
    public String[][][] objectsReceivedString2D;
    int fileRequestedCount;
    public long[][] optIndexShare;
    public long[] optInvRowVec;
    public long[] optInvColVec;
    public long[] phase2OptInvResult;
    public long[][] optInvServerVerification;
    public BigInteger[] verificationForServer;
    public long[] result;
    public long[][] fileVectorShare;
    public long[][][] phase3ResultThread;
    public long[][][] verificationForServerThreadPhase3;
    public long[][] verificationForServerPhase3;
    public long[] permutReorgVector;
    public long[][] fileIds;
    String stage;
    String stageLabel;

    private String currentPhase = "INIT";
    private boolean searchActive = false;
    List<Integer> neighbors = new ArrayList<>();
    int numThreads = 15;
    String type;

    private ObjectOutputStream clientOutputStream;

    private BlockingQueue<PeerMessage> peerMessageQueue;
    private BlockingQueue<ClientRequest> clientRequestQueue = new LinkedBlockingQueue<>();

    public void initSearch(boolean verification) {
        this.clientVerification = verification;
    }

    public void executeSearch() {
        Instant startTime, endTime;
        try {
            startTime = Instant.now();
            phase1();
            endTime = Instant.now();
            log.log(Level.INFO, "Phase 1 completed in " + (endTime.toEpochMilli() - startTime.toEpochMilli())
                    + " ms for session " + sessionId);
            startTime = Instant.now();
            if (phase2()) {
                return;
            }
            endTime = Instant.now();
            log.log(Level.INFO, "Phase 2 completed in " + (endTime.toEpochMilli() - startTime.toEpochMilli())
                    + " ms for session " + sessionId);
            while (true) {
                phase3();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    // Constructor for client-initiated search
    public ServerMultiParallel(String sessionId, int serverNumber, ServerDispatcher dispatcher) {
        this.sessionId = sessionId;
        this.serverNumber = serverNumber;
        this.dispatcher = dispatcher;
        this.clientRequestQueue = new LinkedBlockingQueue<>();
        // Register session with dispatcher to receive peer messages
        this.peerMessageQueue = dispatcher.registerSession(sessionId);
        setNeighbors();
        initializeDataStructures();
    }

    public void initializeDataStructures() {
        if (dispatcher.initialized == false) {
            return;
        }

        if (dispatcher.serverNumber <= 3) {
            phase1Result = new StringBuilder[numThreads];
            searchKeywordVectorShare = null;
            optIndexShare = null;
            optInvRowVec = null;
            optInvColVec = null;
            phase2AddrResult = new long[dispatcher.addr[0].length];
            phase2AddrResultSlice = new long[dispatcher.addr[0].length - dispatcher.hashBlockCount + 1];
            optInvServerVerification = new long[dispatcher.optCol][dispatcher.hashBlockCount];
            fileVectorShare = null;
            verificationForServerThreadPhase3 = null;
        } else {
            phase1Result = new StringBuilder[numThreads];
        }

    }

    /**
     * to send random shares to neighbouring server
     *
     * @param randomShares the random number shares
     * @throws IOException
     * @throws ClassNotFoundException
     */

    public void helper(long[][] randomShares, int serverCount, int phase)
            throws IOException, ClassNotFoundException, InterruptedException {
        objectsReceivedLong1D = new long[serverCount][];

        if (phase == 1) {
            if (serverCount == 3) {
                // ✅ STEP 1: ALL servers send in parallel threads
                ExecutorService sendExecutor = Executors.newFixedThreadPool(2);

                if (serverNumber == 1) {
                    sendExecutor.submit(() -> {
                        try {
                            sendToServer(randomShares[0], neighbors.get(0));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    sendExecutor.submit(() -> {
                        try {
                            sendToServer(randomShares[1], neighbors.get(1));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } else if (serverNumber == 2) {
                    sendExecutor.submit(() -> {
                        try {
                            sendToServer(randomShares[1], neighbors.get(0));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    sendExecutor.submit(() -> {
                        try {
                            sendToServer(randomShares[2], neighbors.get(1));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } else {
                    sendExecutor.submit(() -> {
                        try {
                            sendToServer(randomShares[2], neighbors.get(0));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    sendExecutor.submit(() -> {
                        try {
                            sendToServer(randomShares[0], neighbors.get(1));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }

                sendExecutor.shutdown();

                // ✅ WAIT for ALL sends to complete before ANY server starts receiving
                if (!sendExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IOException("Send timeout - not all servers sent data");
                }

                System.out.println("Server " + serverNumber + ": All sends complete, now receiving...");

                // ✅ STEP 2: NOW all servers can receive safely
                List<Object> objects = startServerMultiWithTimeout(30000);
                type = "1D";
                readObjectsAsLong(type, objects);
            } else { // serverCount == 4
                     // Same pattern for 4 servers

                // STEP 1: All servers send first

                ExecutorService sendExecutor = Executors.newFixedThreadPool(3);

                // Use a loop to send to all neighbors in parallel
                for (int i = 0; i < neighbors.size(); i++) {
                    final int neighborIdx = i;
                    sendExecutor.submit(() -> {
                        try {
                            int neighborSN = neighbors.get(neighborIdx);
                            int targetX;

                            // Map the physical Server Number to its mathematical x-coordinate (Label)
                            if (neighborSN <= 3) {
                                targetX = Math.floorMod(neighborSN - 2, 3) + 1; // S1=3, S2=1, S3=2
                            } else {
                                targetX = 4; // S4=4
                            }

                            // The index in randomShares is always (targetX - 1)
                            int shareIdx = targetX - 1;

                            sendToServer(randomShares[shareIdx], neighborSN);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }

                sendExecutor.shutdown();
                sendExecutor.awaitTermination(10, TimeUnit.SECONDS);

                // STEP 2: All servers receive
                List<Object> objectsList = startServerMultiWithTimeout(30000); // 30 second timeout

                type = "1D";
                objectsReceivedLong1D = new long[serverCount][];
                readObjectsAsLong(type, objectsList);

            }

        } else if (phase == 2) {
            if (serverCount == 3) {
                // Same send-all-first pattern for phase 2

                // STEP 1: All servers send first
                if (serverNumber == 1) {
                    sendToServer(randomSharesPhase2[1], neighbors.get(0));
                    sendToServer(randomSharesPhase2[2], neighbors.get(1));
                } else if (serverNumber == 2) {
                    sendToServer(randomSharesPhase2[2], neighbors.get(0));
                    sendToServer(randomSharesPhase2[0], neighbors.get(1));
                } else { // Server 3
                    sendToServer(randomSharesPhase2[0], neighbors.get(0));
                    sendToServer(randomSharesPhase2[1], neighbors.get(1));
                }

                Thread.sleep(100);

                // STEP 2: All servers receive
                List<Object> objectsList = startServerMultiWithTimeout(30000);

                type = "1D";
                objectsReceivedLong1D = new long[serverCount][];
                readObjectsAsLong(type, objectsList);
            }
        }
    }

    /**
     * NEW: Start server with timeout to prevent deadlock
     * This is the main fix - adds timeout to prevent infinite blocking
     */
    public List<Object> startServerMultiWithTimeout(long timeoutMs) throws InterruptedException {
        int messagesToWaitFor = serverCount - 1;
        System.out.println("Main Thread: Waiting for " + messagesToWaitFor +
                " peer message(s) with " + timeoutMs + "ms timeout...");

        List<Object> objectsList = new ArrayList<>();

        for (int i = 0; i < messagesToWaitFor; i++) {
            // CRITICAL FIX: Use poll() with timeout instead of take()
            PeerMessage message = peerMessageQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (message == null) {
                // Timeout occurred - this prevents deadlock!
                String errorMsg = "TIMEOUT: Did not receive message " + (i + 1) + " of " +
                        messagesToWaitFor + " within " + timeoutMs + "ms. Possible deadlock or network issue.";
                System.err.println(errorMsg);
                log.log(Level.SEVERE, errorMsg);
                throw new InterruptedException(errorMsg);
            }

            System.out.println("Main Thread: Took a message from Peer " + message.getOriginServer());
            objectsList.add(message.getPayload());
        }

        System.out.println("Main Thread: Received all necessary peer messages.");

        return objectsList;
    }

    /**
     * NEW: Overloaded version for 4-server scenario
     */
    public List<Object> startServerMultiWithTimeout(int x, long timeoutMs)
            throws IOException, ClassNotFoundException, InterruptedException {

        int expectedMessages;

        if (serverNumber <= 2) {
            expectedMessages = serverCount - 1;
        } else if (serverNumber == 3 || serverNumber == 4) {
            expectedMessages = x;
        } else {
            expectedMessages = 0;
        }

        System.out.println("Main Thread: Waiting for " + expectedMessages +
                " peer message(s) with " + timeoutMs + "ms timeout...");

        List<Object> objectsList = new ArrayList<>();

        for (int i = 0; i < expectedMessages; i++) {
            PeerMessage peerMessage = peerMessageQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (peerMessage == null) {
                String errorMsg = "TIMEOUT: Did not receive message " + (i + 1) + " of " +
                        expectedMessages + " within " + timeoutMs + "ms";
                System.err.println(errorMsg);
                log.log(Level.SEVERE, errorMsg);
                throw new InterruptedException(errorMsg);
            }

            System.out.println("Received a message from Peer " + peerMessage.getOriginServer());
            objectsList.add(peerMessage.getPayload());
        }

        return objectsList;
    }

    /**
     * Create random number's shares
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void createRandomShares(int serverCount, int phase)
            throws IOException, ClassNotFoundException, InterruptedException {

        // 1. Determine this server's mathematical x-coordinate (Label)
        // S1=3, S2=1, S3=2, S4=4
        int myX = (serverNumber <= 3) ? (Math.floorMod(serverNumber - 2, 3) + 1) : 4;
        int myIndex = myX - 1; // 0-based index for arrays

        if (phase == 1) {
            System.out.println("Entered into createRandomShares Phase 1 (SN=" + serverNumber + ", x=" + myX + ")");

            long prgServer;
            long[] shares;
            Random seedGenerator = new Random(serverNumber);

            // Generate shares for all x-coordinates (1 to serverCount)
            long[][] randomSharesPhase1 = new long[serverCount][dispatcher.keywordCount + 1];
            for (int i = 0; i < dispatcher.keywordCount; i++) {
                prgServer = seedGenerator.nextLong(Constant.getMaxRandomBound() -
                        Constant.getMinRandomBound()) + Constant.getMinRandomBound();

                // shamirSecretShares returns share[0] for x=1, share[1] for x=2, etc.
                shares = shamirSecretShares(prgServer, serverCount);
                for (int j = 0; j < shares.length; j++) {
                    randomSharesPhase1[j][i] = shares[j];
                }
            }

            // Add the server label to the end of each row for the peer to identify it
            for (int j = 0; j < serverCount; j++) {
                randomSharesPhase1[j][dispatcher.keywordCount] = myX;
            }

            // Send out the random shares to peers using the corrected helper mapping
            helper(randomSharesPhase1, serverCount, phase);

            // Sum the local share and all received shares for THIS specific x-coordinate
            randomSharesPhase1Sum = new long[dispatcher.keywordCount];
            for (int k = 0; k < dispatcher.keywordCount; k++) {
                // Start with this server's own share for its seat (myX)
                long sum = randomSharesPhase1[myIndex][k];

                // Add non-null shares received from peers (they are already sorted by label in
                // readObjectsAsLong)
                for (int j = 0; j < serverCount; j++) {
                    if (objectsReceivedLong1D[j] != null) {
                        sum = (sum + objectsReceivedLong1D[j][k]) % dispatcher.modValue;
                    }
                }
                randomSharesPhase1Sum[k] = sum;
            }

        } else if (phase == 2) {
            // Apply the same coordinate-aware logic for Phase 2
            Random seedGenerator = new Random(serverNumber);
            randomSharesPhase2 = new long[serverCount][dispatcher.optCol + 1];
            randomSharesPhase2Sum = new long[dispatcher.optCol];
            long prgServer;
            long[] shares;

            for (int i = 0; i < dispatcher.optCol; i++) {
                prgServer = (seedGenerator.nextLong(Constant.getMaxRandomBound() -
                        Constant.getMinRandomBound()) + Constant.getMinRandomBound());
                shares = shamirSecretShares(prgServer, serverCount);
                for (int j = 0; j < shares.length; j++) {
                    randomSharesPhase2[j][i] = shares[j];
                }
            }

            for (int j = 0; j < serverCount; j++) {
                randomSharesPhase2[j][dispatcher.optCol] = serverNumber;
            }

            helper(randomSharesPhase2, serverCount, phase);

            for (int i = 0; i < dispatcher.optCol; i++) {
                prgServer = 0;
                for (int j = 0; j < serverCount; j++) {
                    if (j != serverNumber - 1) {
                        prgServer = (prgServer + objectsReceivedLong1D[j][i]) % dispatcher.modValue;
                    } else {
                        prgServer = (prgServer + randomSharesPhase2[j][i]) % dispatcher.modValue;
                    }
                }
                randomSharesPhase2Sum[i] = prgServer;
            }
        }
    }

    /**
     * Create share for secret value as long
     *
     * @param secret      the secret value as long
     * @param serverCount the number of servers for which shares are to be created
     * @return the share of the secret value as long is returned for given number of
     *         server as servercount
     */
    public long[] shamirSecretShares(long secret, int serverCount) {
        Random rand = new Random();
        long[] share = new long[serverCount];
        // choosing the slope value for line
        long slope = rand.nextInt(Constant.getMaxRandomBound() - Constant.getMinRandomBound()) +
                Constant.getMinRandomBound();
        // check if the secret size is enough for long or BigInteger calculation
        for (int i = 0; i < serverCount; i++) {
            share[i] = (((i + 1) * slope) + secret) % dispatcher.modValue;
        }
        return share;
    }

    /**
     * To read values from objects
     *
     * @param type the type of object as 1D or 2D
     */
    public void readObjectsAsLong(String type, List<Object> objectsList) {
        if (type.equals("1D")) {
            objectsReceivedLong1D = new long[serverCount][];
            for (int i = 0; i < objectsList.size(); i++) {
                int temp = (int) (((long[]) objectsList.get(i))[((long[]) objectsList.get(i)).length - 1]);
                objectsReceivedLong1D[temp - 1] = ((long[]) objectsList.get(i));
            }
        } else if (type.equals("2D")) {
            for (int i = 0; i < objectsList.size(); i++) {
                long[][] share = (long[][]) objectsList.get(i);
                int temp = (int) (share[share.length - 1][0]);
                serverVerificationShares2D[temp - 1] = share;
            }
        }
    }

    /**
     * To read values from objects
     *
     * @param type the type of object as 1D or 2D
     */
    public void readObjectsAsString(String type, List<Object> objectsList, int fileRequestedCount) {
        if (type.equals("1D")) {
            for (int i = 0; i < objectsList.size(); i++) {
                String objectRead = new String((byte[]) objectsList.get(i), StandardCharsets.UTF_8);
                int temp = objectRead.charAt(objectRead.length() - 1) - '0';
                objectsReceivedString1D[temp - 1] = objectRead.split(",");
            }
        } else if (type.equals("2D")) {
            for (int i = 0; i < objectsList.size(); i++) {
                byte[][] data = (byte[][]) objectsList.get(i);
                int temp = new String(data[data.length - 1], StandardCharsets.UTF_8).charAt(0) - '0';
                objectsReceivedString2D[temp - 1] = new String[fileRequestedCount][];
                for (int j = 0; j < data.length - 1; j++) {
                    objectsReceivedString2D[temp - 1][j] = new String(data[j], StandardCharsets.UTF_8).split(",");
                }
            }
        }

    }

    /**
     * to send data to client
     * 
     * @param data
     */
    public void sendToClient(Object data) {
        try {
            clientOutputStream.writeObject(data);
            clientOutputStream.flush();
        } catch (IOException ex) {
            log.log(Level.SEVERE, ex.getMessage());
        }
    }

    /**
     * to send data to a server
     *
     * @param data         the data
     * @param serverNumber the number of target server
     */
    // In Server.java
    /*
     * A thread-safe method to send a message to a peer server.
     * Uses local variables and try-with-resources to prevent race conditions and
     * resource leaks.
     **/
    public void sendToServer(Object data, int serverNumber) {
        try {

            // Correctly wrap the data in a PeerMessage before sending.
            PeerMessage messageToSend = new PeerMessage(sessionId, serverNumber, data);
            dispatcher.serverConnections.get(serverNumber).send(messageToSend);

        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to send message from Server " + this.serverNumber + " to peer" + serverNumber,
                    e);
        }
    }

    // /**
    // * to start server to listen for multiple items
    // *
    // * @throws IOException
    // * @throws ClassNotFoundException
    // */
    // public void startServerMulti() throws InterruptedException {
    // // Determine how many peer messages we need to wait for.
    // int messagesToWaitFor = (serverNumber != 3) ? (serverCount - 1) : 1;
    // System.out.println("Main Thread: Waiting for " + messagesToWaitFor + " peer
    // message(s)...");

    // // Clear the list from any previous operation.
    // objectsList.clear();

    // // Loop until we have taken the required number of messages from the queue.
    // for (int i = 0; i < messagesToWaitFor; i++) {
    // // This call BLOCKS and waits for the Listener to put a message in the
    // // peerMessageQueue.
    // PeerMessage message = peerMessageQueue.take();

    // System.out.println("Main Thread: Took a message from Peer " +
    // message.getPort());
    // objectsList.add(message.getPayload());
    // }
    // System.out.println("Main Thread: Received all necessary peer messages.");
    // }

    // /**
    // * to start server to listen for multiple items
    // *
    // * @throws IOException
    // * @throws ClassNotFoundException
    // */
    // public void startServerMulti(int x) throws IOException,
    // ClassNotFoundException, InterruptedException {
    // if (serverNumber <= 2) {
    // while (objectsList.size() != (serverCount - 1)) {
    // // Reading data from the server
    // PeerMessage peerMessage = peerMessageQueue.take();
    // System.out.println("Recevied a message");
    // objectsList.add(peerMessage.getPayload());
    // }
    // } else if (serverNumber == 3) {
    // while (objectsList.size() != x) {
    // // Reading data from the server
    // PeerMessage peerMessage = peerMessageQueue.take();
    // System.out.println("Recevied a message");
    // objectsList.add(peerMessage.getPayload());
    // }
    // } else if (serverNumber == 4) {
    // while (objectsList.size() != x) {
    // // Reading data from the server
    // PeerMessage peerMessage = peerMessageQueue.take();
    // System.out.println("Recevied a message");
    // objectsList.add(peerMessage.getPayload());
    // }
    // }
    // }

    /**
     * to perform hash digest of given data using SHA-1
     *
     * @param data The given data
     * @return The numeric hash digest value of 20B
     * @throws NoSuchAlgorithmException
     */
    public String hashDigest(String data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(data.getBytes());
        byte[] digest = md.digest();
        BigInteger result = (new BigInteger(digest)).mod(dispatcher.modValueBig);
        return result.toString();
    }

    /**
     * To interpolate share values to retrieve secret
     *
     * @param share the shares
     * @return the cleartext/interpolated value
     */
    public long lagrangeInterpolation(long[] share) {
        return switch (share.length) {
            case 2 -> Math.floorMod((((2 * share[0]) % dispatcher.modValue) - share[1]), dispatcher.modValue);
            case 3 -> (Math.floorMod((((3 * share[0]) % dispatcher.modValue) -
                    ((3 * share[1]) % dispatcher.modValue)), dispatcher.modValue) + share[2]) % dispatcher.modValue;
            default -> throw new IllegalStateException("Unexpected value: " + share.length);
        };
    }

    /**
     * To interpolate share values to retrieve secret
     *
     * @param share the shares
     * @return the cleartext/interpolated value
     */
    public BigInteger lagrangeInterpolation(BigInteger share[]) {
        return switch (share.length) {
            case 2 -> ((BigInteger.valueOf(2).multiply(share[0])).mod(dispatcher.modValueBig)
                    .subtract(share[1])).mod(dispatcher.modValueBig);
            case 3 -> (((BigInteger.valueOf(3)
                    .multiply(share[0])).mod(dispatcher.modValueBig).subtract(
                            (BigInteger.valueOf(3)
                                    .multiply(share[1])).mod(dispatcher.modValueBig)))
                    .mod(dispatcher.modValueBig).add(share[2])).mod(dispatcher.modValueBig);
            default -> throw new IllegalStateException("Unexpected value: " + share.length);
        };
    }

    /**
     * to send IPs of neighboring server
     */
    public void setNeighbors() {
        switch (serverNumber) {
            case 1:
                neighbors.addAll(List.of(2, 3, 4));
                break;
            case 2:
                neighbors.addAll(List.of(3, 1, 4));
                break;
            case 3:
                neighbors.addAll(List.of(1, 2, 4));
                break;
            case 4:
                neighbors.addAll(List.of(1, 2, 3));
                break;
        }
    }

    /**
     * Performs a sequential "ring exchange" with a neighboring server.
     * The if/else structure is required to prevent deadlock in this pattern.
     *
     * @param data The data to pass to the next server in the ring.
     * @return The data received from the previous server in the ring.
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     */
    public Object communicateAllServers(long[] data, boolean temp)
            throws IOException, ClassNotFoundException, InterruptedException {

        // For a ring exchange, one server must initiate to prevent gridlock.
        // In this protocol, Server 1 is the initiator.
        if (serverNumber == 1) {
            // Server 1 sends its data first.
            System.out.println("Server 1: Sending data to next peer...");
            sendToServer(data, neighbors.get(0));

            // Then it waits to receive data from the last server in the ring.
            System.out.println("Server 1: Waiting for data from previous peer...");
            PeerMessage receivedMessage = peerMessageQueue.take();
            return receivedMessage.getPayload();
        } else {
            // All other servers wait to receive data first.
            System.out.println("Server " + serverNumber + ": Waiting for data from previous peer...");
            PeerMessage receivedMessage = peerMessageQueue.take();

            // After receiving, they send their own data to the next server.
            System.out.println("Server " + serverNumber + ": Sending data to next peer...");
            sendToServer(data, neighbors.get(0));
            return receivedMessage.getPayload();
        }
    }

    /**
     * Broadcasts data to all peers and gathers their responses in parallel.
     * This single method replaces the entire old if/else if/else block.
     *
     * @param data The data to be broadcast to other servers.
     * @throws InterruptedException
     */
    public void communicateAllServers(long[][] data) throws InterruptedException {
        // For this example, let's assume you have a list of peer IPs and Ports.
        // This is more scalable than hardcoding variables for each neighbor.
        int peerCount = 2;

        // -----------------------------------------------------------
        // 1. BROADCAST: Send to all peers in parallel.
        // -----------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(peerCount);

        System.out.println("Server " + serverNumber + ": Broadcasting data to " + peerCount + " peers...");
        for (int i = 0; i < peerCount; i++) {
            final int peerIndex = i; // Use a final variable for the lambda
            executor.submit(() -> {
                try {
                    // Your existing sendToServer is perfect here.
                    sendToServer(data, neighbors.get(peerIndex));
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Broadcast failed for peer " + peerIndex, e);
                }
            });
        }

        // Wait for all the parallel "send" tasks to complete.
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Server " + serverNumber + ": Broadcast complete.");

        // 2. GATHER: Receive data from all peers.
        System.out.println("Server " + serverNumber + ": Gathering " + peerCount + " responses...");

        List<Object> objectsList = new ArrayList<>();

        for (int i = 0; i < peerCount; i++) {
            // This takes messages from the queue that the Listener is populating in the
            // background.
            PeerMessage message = peerMessageQueue.take();
            objectsList.add(message.getPayload());
            System.out.println("Server " + serverNumber + ": Gathered message from Peer " + message.getOriginServer());
        }
        System.out.println("Server " + serverNumber + ": Gather complete.");

        // 3. PROCESS: Now all the gathered data is processed
        serverVerificationShares2D = new long[serverCount][][];
        type = "2D";
        readObjectsAsLong(type, objectsList);
    }

    public record PeerConnection(String ip, int port) {
    }

    /**
     * A refactored method to handle both ring and broadcast peer communication.
     *
     * @param data       The data to be shared.
     * @param neighbours The number of peers to communicate with.
     * @return The received data object, primarily for the ring exchange.
     * @throws InterruptedException
     */
    public Object communicateAllServers(long[] data, int neighbours) throws InterruptedException {

        // A helper list to dynamically select peers to talk to.
        List<Integer> peers = new ArrayList<>();
        if (neighbours == 1) {
            // For ring communication, we only talk to one specific neighbor.
            // Assuming nextServerIp2/Port2 is the "previous" server in the ring.
            peers.add(neighbors.get(1));
        } else if (neighbours == 2) {
            // For broadcast, we talk to all other primary servers.
            peers.add(neighbors.get(0));
            peers.add(neighbors.get(1));
        }

        // --------------------------------------------------------------------
        // CASE 1: Ring Exchange (Send to 1, Receive from 1)
        // --------------------------------------------------------------------
        if (neighbours == 1) {
            // This logic must be ordered to prevent deadlock.
            if (serverNumber == 1) {
                // Server 1 sends first, then receives.
                sendToServer(data, peers.get(0));
                return peerMessageQueue.take().getPayload();
            } else {
                // Other servers receive first, then send.
                Object receivedData = peerMessageQueue.take().getPayload();
                sendToServer(data, peers.get(0));
                return receivedData;
            }
        }

        // --------------------------------------------------------------------
        // CASE 2: All-to-All Broadcast (Send to all, Receive from all)
        // --------------------------------------------------------------------
        else if (neighbours == 2) {
            // This single block of code works for ALL servers (1, 2, and 3).

            // 1. BROADCAST: Send to all peers in parallel.
            ExecutorService executor = Executors.newFixedThreadPool(peers.size());
            for (Integer peer : peers) {
                executor.submit(() -> sendToServer(data, peer));
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // 2. GATHER: Receive from all peers.
            List<Object> objectsList = new ArrayList<>();
            for (int i = 0; i < peers.size(); i++) {
                objectsList.add(peerMessageQueue.take().getPayload());
            }

            // 3. PROCESS: Use the gathered data.
            type = "1D";
            objectsReceivedLong1D = new long[serverCount][];
            readObjectsAsLong(type, objectsList);
        }

        return null; // This path is only taken for the broadcast case.
    }

    /**
     * A refactored method to broadcast a byte array to all peers and gather their
     * responses.
     * This single method replaces the entire old if/else if/else block.
     *
     * @param data The byte array data to be broadcast.
     * @throws InterruptedException
     */
    public void communicateAllServers(byte[] data) throws InterruptedException {
        // This assumes you have a list or array of the peers you need to talk to.
        // This is much more scalable than separate variables for each neighbor.
        int peerCount = 2;

        // -----------------------------------------------------------
        // 1. BROADCAST: Send data to all peers in parallel.
        // -----------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(peerCount);

        System.out.println("Server " + serverNumber + ": Broadcasting byte[] data to " + peerCount + " peers...");
        for (int i = 0; i < peerCount; i++) {
            final int peerIndex = i;
            executor.submit(() -> {
                try {
                    sendToServer(data, neighbors.get(peerIndex));
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Broadcast failed for peer " + neighbors.get(peerIndex), e);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Server " + serverNumber + ": Broadcast complete.");

        // -----------------------------------------------------------
        // 2. GATHER: Receive responses from all peers.
        // -----------------------------------------------------------
        List<Object> objectsList = new ArrayList<>();
        System.out.println("Server " + serverNumber + ": Gathering " + peerCount + " byte[] responses...");
        for (int i = 0; i < peerCount; i++) {
            // Take messages from the queue that the Listener is populating.
            PeerMessage message = peerMessageQueue.take();
            objectsList.add(message.getPayload());
        }
        System.out.println("Server " + serverNumber + ": Gather complete.");

        // -----------------------------------------------------------
        // 3. PROCESS: Now that all data is gathered, process it.
        // -----------------------------------------------------------
        type = "1D";
        objectsReceivedString1D = new String[serverCount][];
        readObjectsAsString(type, objectsList, fileRequestedCount);

    }

    /**
     * A refactored method to broadcast a byte[][] to all peers and gather their
     * responses.
     * This single method replaces the entire old if/else if/else block.
     *
     * @param data The byte[][] data to be broadcast.
     * @throws InterruptedException
     */
    public void communicateAllServers(byte[][] data) throws InterruptedException {
        // This assumes you have a list of the peers you need to talk to.
        int peerCount = 2;

        // -----------------------------------------------------------
        // 1. BROADCAST: Send data to all peers in parallel.
        // -----------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(peerCount);

        System.out.println("Server " + serverNumber + ": Broadcasting byte[][] data to " + peerCount + " peers...");
        for (int i = 0; i < peerCount; i++) {
            final int peerIndex = i;
            executor.submit(() -> {
                try {
                    sendToServer(data, neighbors.get(peerIndex));
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Broadcast failed for peer " + neighbors.get(peerIndex), e);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Server " + serverNumber + ": Broadcast complete.");

        // -----------------------------------------------------------
        // 2. GATHER: Receive responses from all peers.
        // -----------------------------------------------------------
        List<Object> objectsList = new ArrayList<>();
        System.out.println("Server " + serverNumber + ": Gathering " + peerCount + " byte[][] responses...");
        for (int i = 0; i < peerCount; i++) {
            // Take messages from the queue populated by the Listener.
            PeerMessage message = peerMessageQueue.take();
            objectsList.add(message.getPayload());
        }
        System.out.println("Server " + serverNumber + ": Gather complete.");

        // -----------------------------------------------------------
        // 3. PROCESS: Now that all data is gathered, process it.
        // -----------------------------------------------------------
        objectsReceivedString2D = new String[serverCount][][];
        type = "2D";
        readObjectsAsString(type, objectsList, fileRequestedCount);

    }

    /**
     * To compare search keyword with server keyword and access value for the client
     * multithreaded across number of keywords
     */
    public void task11() {
        BigInteger[] keywords = dispatcher.act[0];
        BigInteger[] access = dispatcher.act[clientId];

        String[] tempResults = new String[dispatcher.keywordCount];

        IntStream.range(0, dispatcher.keywordCount).parallel().forEach(i -> {
            try {
                BigInteger temp = (((keywords[i].subtract(searchKeywordShare)).mod(dispatcher.modValueBig)
                        .add(access[i]))
                        .mod(dispatcher.modValueBig)
                        .multiply(BigInteger.valueOf(randomSharesPhase1Sum[i]))).mod(dispatcher.modValueBig);

                tempResults[i] = temp.toString();
            } catch (Exception e) {
                System.err.println("Crash at keyword index i=" + i);
                e.printStackTrace();
            }
        });
        for (int i = 0; i < tempResults.length; i++) {
            phase1Result[0].append(tempResults[i]).append(",");
        }
    }

    /**
     * perform dot product of keyword vector with addr table
     * multithreaded across the column i.e. the content of addr table
     *
     * @param threadNum the number of threads
     */
    public void task21() {
        // 1. Extract constants to avoid looking them up repeatedly
        final int addrLen = dispatcher.addr[0].length;
        final int keywordCount = dispatcher.keywordCount;
        final long mod = dispatcher.modValue;

        IntStream.range(0, addrLen).parallel().forEach(j -> {
            try {

                // 3. The 'i' loop gets pushed to the INSIDE
                for (int i = 0; i < keywordCount; i++) {

                    // Fetch the row data
                    long[] temp = dispatcher.addr[i];

                    // 4. Perfectly thread-safe math!
                    phase2AddrResult[j] = (phase2AddrResult[j] +
                            (temp[j] * searchKeywordVectorShare[i]) % mod) % mod;
                }

            } catch (Exception e) {
                System.err.println("Crash at column index j=" + j);
                e.printStackTrace();
            }
        });
    }

    public void task22() {

        final int optCol = dispatcher.optCol;
        final int optRow = dispatcher.optRow;
        final int colVecLen = optInvColVec.length;
        final long mod = dispatcher.modValue;

        IntStream.range(0, optCol).parallel().forEach(j -> {
            try {
                for (int i = 0; i < optRow; i++) {
                    int flatIndex = i * colVecLen + j;
                    for (int k = 0; k < 3; k++) {
                        optInvServerVerification[j][k] = (optInvServerVerification[j][k] +
                                (dispatcher.optInvHashValues[flatIndex][k]
                                        * optInvRowVec[i]) % mod)
                                % mod;
                    }
                    phase2OptInvResult[j] = (phase2OptInvResult[j] +
                            (dispatcher.optInv[flatIndex] * optInvRowVec[i]) % mod)
                            % mod;
                }

            } catch (Exception e) {
                System.err.println("Crash at column index j=" + j);
                e.printStackTrace();
            }
        });
    }

    /**
     * to perform dot product with row vector and inverted index
     * multithreaded across the number of rows in opt inverted index
     */
    public void task23() {

        IntStream.range(0, dispatcher.optCol).parallel().forEach(j -> {
            try {
                for (int i = 0; i < dispatcher.optRow; i++) {
                    phase2OptInvResult[j] = (phase2OptInvResult[j] +
                            (dispatcher.optInv[i * dispatcher.optCol + j] * optInvRowVec[i]) % dispatcher.modValue)
                            % dispatcher.modValue;
                }
            } catch (Exception e) {
                System.err.println("Crash at column index j=" + j);
                e.printStackTrace();
            }

        });
    }

    /**
     * To perform dot product with addr table, act's client access and keyword list
     * multithreaded across number of keywords
     *
     */
    public void task24() {
        final int addrLen = dispatcher.addr[0].length;
        final int maxJ = addrLen + 2;
        final int keywordCount = dispatcher.keywordCount;

        final BigInteger[] keywords = dispatcher.act[0];
        final BigInteger[] access = dispatcher.act[clientId];

        verificationForServer[0] = BigInteger.ZERO;
        verificationForServer[1] = BigInteger.ZERO;

        IntStream.range(0, maxJ).parallel().forEach(j -> {
            try {
                for (int i = 0; i < keywordCount; i++) {
                    long[] temp = dispatcher.addr[i];
                    if (j < dispatcher.addr[0].length) {
                        phase2AddrResult[j] = (phase2AddrResult[j] +
                                (temp[j] * searchKeywordVectorShare[i]) % dispatcher.modValue) % dispatcher.modValue;
                    } else if (j == dispatcher.addr[0].length) {
                        verificationForServer[0] = ((keywords[i]
                                .multiply(BigInteger.valueOf(searchKeywordVectorShare[i])))
                                .mod(dispatcher.modValueBig).add(verificationForServer[0])).mod(dispatcher.modValueBig);
                    } else if (j == dispatcher.addr[0].length + 1) {
                        verificationForServer[1] = ((access[i]
                                .multiply(BigInteger.valueOf(searchKeywordVectorShare[i])))
                                .mod(dispatcher.modValueBig).add(verificationForServer[1])).mod(dispatcher.modValueBig);
                    }
                }
            } catch (Exception e) {
                System.err.println("Crash at column index j=" + j);
                e.printStackTrace();
            }
        });
    }

    /**
     * to perform dot product with column vector and result from task22
     * multithreaded across number of keywords
     */
    public void task25() {
        IntStream.range(0, dispatcher.hashBlockCount).parallel().forEach(j -> {
            try {
                for (int i = 0; i < dispatcher.optCol; i++) {
                    result[j] = (result[j] + (Math.floorMod((1 - optInvColVec[i]), dispatcher.modValue)
                            * optInvServerVerification[i][j]) % dispatcher.modValue) % dispatcher.modValue;
                }
            } catch (Exception e) {
                System.err.println("Crash at column index j=" + j);
                e.printStackTrace();
            }
        });
    }

    /**
     * performs dot product between file keyword matrix and file vector , file data
     * matrix and file vector
     * multithreaded across number of files
     */
    public void task31() {

        if (dispatcher.fileCount == 0)
            return;

        // 1. Get the max length for K
        final int maxK = dispatcher.fileLength;

        // 2. Automatically detect how many CPU cores the server has
        final int cores = Runtime.getRuntime().availableProcessors();
        final int batchSize = (int) Math.ceil(maxK / (double) cores);

        // 3. The parallel stream hands out "Batch Numbers" instead of single 'k'
        // indices!
        IntStream.range(0, cores).parallel().forEach(batchNum -> {
            try {
                // Calculate the exact K range for this specific thread's chunk
                int startK = batchNum * batchSize;
                int endK = Math.min(startK + batchSize, maxK);

                // If this batch happens to be out of bounds, skip it
                if (startK >= maxK)
                    return;

                // 4. The loops are back in the correct order for the CPU Cache!
                for (int i = 0; i < fileRequestedCount; i++) {
                    for (int j = 0; j < dispatcher.fileCount; j++) {

                        // --- VERIFICATION MATH ---
                        long[] tempKeyword = dispatcher.fileKeyword[j];

                        // K is the innermost loop again! L1 Cache is fully utilized!
                        for (int k = startK; k < Math.min(endK, tempKeyword.length); k++) {
                            // Assuming you flattened your 3D array to a 2D array:
                            verificationForServerPhase3[i][k] = (verificationForServerPhase3[i][k]
                                    + (tempKeyword[k] * fileVectorShare[i][j]) % dispatcher.modValue)
                                    % dispatcher.modValue;
                        }

                        // --- PHASE 3 RESULT MATH ---
                        long[] tempFiles = dispatcher.files[j];

                        for (int k = startK; k < Math.min(endK, tempFiles.length); k++) {
                            // Writing directly to the final target array! No task32 needed!
                            phase3Result[i][k] = (phase3Result[i][k]
                                    + (fileVectorShare[i][j] * tempFiles[k]) % dispatcher.modValue)
                                    % dispatcher.modValue;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Crash at batch number: " + batchNum);
                e.printStackTrace();
            }
        });
    }

    /**
     * to aggregate the file content data across threads
     */
    public void task32() {

        IntStream.range(0, dispatcher.fileLength).parallel().forEach(k -> {
            try {
                for (int i = 0; i < fileRequestedCount; i++) {
                    for (int j = 0; j < numThreads; j++) {
                        phase3Result[i][k] = (phase3Result[i][k] + phase3ResultThread[i][j][k]) % dispatcher.modValue;
                    }
                }
            } catch (Exception e) {
                System.err.println("Crash at Phase 3 stream, k=" + k);
                e.printStackTrace();
            }
        });
    }

    /**
     * to rotate the vector with given permutation
     *
     * @param direction     the direction clock or anti-clock
     * @param rotationValue the value of rotation
     * @return the rotated array
     */
    public long[] rotateVector(int direction, int rotationValue) {

        long[] rotatedVector = new long[permutReorgVector.length];

        if (direction == 1) { // left rotation

            for (int i = 0; i < permutReorgVector.length; i++) {
                int index = Math.floorMod((i - rotationValue), permutReorgVector.length);
                rotatedVector[index] = permutReorgVector[i];
            }
        } else if (direction == 0) { // right rotation
            for (int i = 0; i < permutReorgVector.length; i++) {
                int index = ((i + rotationValue) % permutReorgVector.length);
                rotatedVector[index] = permutReorgVector[i];
            }
        }
        return rotatedVector;
    }

    /**
     * perform test 3, 4, 5, 3= sum(arr), 4= sum(arr*arr), 5=4-3
     *
     * @param arr the input array
     * @return the reuslt of test 3, 4,5
     */
    public long[][] binaryCompute(long[][] arr) {
        long[][] result = new long[arr.length + 1][3];
        for (int i = 0; i < arr.length; i++) {
            long resultA = 0, resultA2 = 0, temp;
            for (int j = 0; j < arr[0].length; j++) {
                temp = (arr[i][j] * arr[i][j]) % dispatcher.modValue;
                resultA = (resultA + arr[i][j]) % dispatcher.modValue;
                resultA2 = (resultA2 + temp) % dispatcher.modValue;
                result[i][0] = resultA;
                result[i][1] = resultA2;
            }
            result[i][2] = fileIds[i][0];
        }
        result[result.length - 1][0] = Math.floorMod(serverNumber - 2, serverCount) + 1;
        return result;
    }

    /**
     * to interpolate the array
     *
     * @param neighbourData the data from neighbouring server
     * @param ownData       the data computed by individual server
     * @param serverCount   the number of servers
     * @return the interpolated value
     */
    public long[][] interpolate(long[][][] neighbourData, long[][] ownData, int serverCount) {
        long[] shares = new long[serverCount];
        long[][] result = new long[ownData.length - 1][ownData[0].length];
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[0].length; j++) {
                for (int l = 0; l < serverCount; l++) {
                    if (l == (Math.floorMod(serverNumber - 2, serverCount))) {
                        shares[l] = ownData[i][j];
                    } else {
                        shares[l] = neighbourData[l][i][j];
                    }
                }
                result[i][j] = lagrangeInterpolation(shares);
            }
        }
        return result;
    }

    /**
     * check the correctness of result
     *
     * @param data the matrix
     * @return result true or false
     */
    public boolean checkBinary(long[][] data) {
        boolean flag = true;
        for (int i = 0; i < fileRequestedCount; i++) {
            if (data[i][0] != 1 || data[i][1] != 1 || data[i][2] != 0) {
                flag = false;
                break;
            }
        }
        return flag;
    }

    /**
     * For fetching the index of search keyword from ACT table
     *
     * @throws IOException
     */
    public void phase1() throws InterruptedException, IOException, ClassNotFoundException {
        System.out.println("Starting phase 1 with Client Verification: " + clientVerification);
        dispatcher.keywordCount = dispatcher.act[0].length;
        int phase = 1;
        if (!clientVerification) { // off serverVerification
            stage = "1";
            serverCount = 3;

            BigInteger[] searchKeywordShare = (BigInteger[]) clientRequestQueue.take().getPayload();

            this.searchKeywordShare = searchKeywordShare[0];
            this.clientId = (int) (searchKeywordShare[1].longValue() + 1);

            phase1Result = new StringBuilder[numThreads];

            // initializing
            for (int i = 0; i < numThreads; i++) {
                phase1Result[i] = new StringBuilder("");
            }

            // create random number shares and share with other server and compute the final
            // sum of random number
            // received from neighbouring servers

            createRandomShares(serverCount, phase);

            // computing phase 1 operation on ACT list
            task11();
            phase1Result[0].append(Math.floorMod(serverNumber - 2, serverCount) + 1);

            // converting to byte array inorder to send biginteger array
            byte[] byteList = phase1Result[0].toString().getBytes(StandardCharsets.UTF_8);
            // System.out.println(byteList.length);
            // sending to client
            sendToClient(byteList);
        } else {
            serverCount = 4;
            // Waiting for client to send the search keyword shares
            Object searchRequest = clientRequestQueue.take().getPayload();
            // reading the data sent by client
            searchKeywordShare = ((BigInteger[]) searchRequest)[0];
            clientId = ((BigInteger[]) searchRequest)[1].intValue() + 1; // since 1st row are keywords and 2nd row are
            // position of keywords
            // initializing
            phase1Result = new StringBuilder[numThreads];

            for (int i = 0; i < numThreads; i++) {
                phase1Result[i] = new StringBuilder("");
            }
            // create random number shares and share with other server and compute the final
            // sum of random number
            // received from neighbouring servers
            createRandomShares(serverCount, phase);

            // computing phase 1 operation on ACT list
            task11();

            if (serverNumber <= 3) {
                phase1Result[0].append(Math.floorMod(serverNumber - 2, 3) + 1);
            } else {
                phase1Result[0].append(4);
            }

            System.out.println(phase1Result[0].toString());
            // converting to byte array
            byte[] byteList = phase1Result[0].toString().getBytes(StandardCharsets.UTF_8);
            // sending to client
            sendToClient(byteList);
        }
    }

    /**
     * To perform phase 2 operation of fetching the file ids for the search keyword
     * from inverted index and addr
     *
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public boolean phase2() throws InterruptedException, IOException, ClassNotFoundException {
        phase2OptInvResult = new long[dispatcher.optCol + 1];
        stage = "2";
        serverCount = 3;
        int phase = 2;
        // Waiting for client to send the data
        // read data send from client
        searchKeywordVectorShare = (long[]) clientRequestQueue.take().getPayload();

        // this will execute if the client is unhappy with servers results in phase 1
        // and wants to end the search op
        if (searchKeywordVectorShare[0] == 0) {
            System.out.println("Client wishes to discontinue the search.");
            log.info("Client wishes to discontinue the search.");
            return true;
        }
        {
            // if verification is required by servers to check if keywords vector sent by
            // client is in accordance to the keyword
            // search by client in phase1 and then returning the addr table data
            clientVerification = false;
            if (clientVerification) {
                verificationForServer = new BigInteger[2];
                task24();
                verificationForServer[0] = (verificationForServer[0].subtract(searchKeywordShare))
                        .mod(dispatcher.modValueBig);

                // for test 3, 4, 5 , 3= sum(A) , 4 = sum(A^2), 5 = 4-3
                // without test 5
                long[] result = new long[2]; // one for sum(A) and one for sum(A^2)
                // with test 5
                // long result[] = new long[2 + keywordCount]; // keywordcount for sum(a^2)
                // -sum(A)
                long temp1;
                for (int i = 0; i < dispatcher.keywordCount; i++) {
                    temp1 = (searchKeywordVectorShare[i] * searchKeywordVectorShare[i]) % dispatcher.modValue;
                    result[0] = (result[0] + searchKeywordVectorShare[i]) % dispatcher.modValue;
                    result[1] = (result[1] + temp1) % dispatcher.modValue;
                    // with test 5
                    // result[i + 2] = (temp1 - searchKeywordVectorShare[i]) % modValue;
                }
                // preparing data to send to neighboring server
                StringBuilder temp = new StringBuilder("");
                temp.append(verificationForServer[0]).append(",").append(verificationForServer[1]).append(",");
                for (long l : result) {
                    temp.append(l).append(",");
                }
                temp.append((Math.floorMod(serverNumber - 2, serverCount) + 1));
                // converting to byte array as we need to send biginteger data also
                byte[] byteList = temp.toString().getBytes(StandardCharsets.UTF_8);
                communicateAllServers(byteList);
                // interpolate the data recived from neighbours
                BigInteger[] resInterBig = new BigInteger[2]; // verification data interpolation
                // without test 5
                long[] resInter = new long[2]; // two tests
                // with test 5
                // long[] resInter = new long[2 + keywordCount];
                long[] sharesLong = new long[serverCount];
                BigInteger[] sharesBig = new BigInteger[serverCount];
                for (int i = 0; i < verificationForServer.length + result.length; i++) {
                    if (i >= 2) { // test 3,4,5
                        for (int p = 0; p < serverCount; p++) {
                            if (p == ((Math.floorMod(serverNumber - 2, serverCount)))) {
                                sharesLong[p] = result[i - 2];
                            } else {
                                sharesLong[p] = Long.parseLong(objectsReceivedString1D[p][i]);
                            }
                        }
                        resInter[i - 2] = lagrangeInterpolation(sharesLong);
                    } else { // test 1,2
                        for (int p = 0; p < serverCount; p++) {
                            if (p == ((Math.floorMod(serverNumber - 2, serverCount)))) {
                                sharesBig[p] = verificationForServer[i];
                            } else {
                                sharesBig[p] = new BigInteger(objectsReceivedString1D[p][i]);
                            }
                        }
                        resInterBig[i] = lagrangeInterpolation(sharesBig);
                    }
                }
                // test the values are interpolated as expected
                boolean flag = resInterBig[0].equals(BigInteger.valueOf(0))
                        && resInterBig[1].equals(BigInteger.valueOf(0))
                        && resInter[0] == 1 && resInter[1] == 1;
                // with test 5
                // if (flag) {
                // for (int i = 2; i < resInterBig.length; i++) {
                // if (resInter[0] != 0) {
                // flag = false;
                // break;
                // }
                // }
                // }

                if (!flag) {
                    log.info("Client has prepared an incorrect keyword vector.");
                    sendToClient(new long[] { 0, serverNumber });
                    return true;
                }
            } else {
                task21();
            }
            // copying everything except hash digest for positions
            System.arraycopy(phase2AddrResult, 0, phase2AddrResultSlice, 0, phase2AddrResultSlice.length);
            phase2AddrResultSlice[phase2AddrResultSlice.length - 1] = (Math.floorMod(serverNumber - 2, serverCount)
                    + 1);
            // sending to client

            sendToClient(phase2AddrResultSlice);
        }

        byte[] syncMsg = "ADDR_SENT".getBytes();
        sendToServer(syncMsg, neighbors.get(0));
        sendToServer(syncMsg, neighbors.get(1));
        // communicateAllServers(syncMsg);

        int received = 0;

        while (received < 2) {
            byte[] addrSentAck = (byte[]) peerMessageQueue.take().getPayload();

            if (new String(addrSentAck).equals("ADDR_SENT")) {
                received++;
                System.out.println("Received ADDR_SENT acknowledgment from peer. Total received: " + received);
            } else {
                System.out.println("Received unexpected message: " + new String(addrSentAck));
            }
        }

        {

            // ✅ NEW: Send optCol and optRow dimensions right after ADDR
            int[] dimensions = { dispatcher.optRow, dispatcher.optCol };
            System.out.println("OPT dimensions: optRow=" + dispatcher.optRow + ", optCol=" + dispatcher.optCol);
            sendToClient(dimensions);

            System.out.println(
                    "✓ Sent opt dimensions to client: optRow=" + dispatcher.optRow + ", optCol=" + dispatcher.optCol);

            // performing operation to get the file ids
            // Waiting for client to send the position and count of data to be retrieved
            // from opt tabel
            optIndexShare = (long[][]) clientRequestQueue.take().getPayload();
            // read object returned from client

            // this will execute if the client is unhappy with servers results in for addr
            if (optIndexShare[0][0] == 0) {
                log.info("Client wishes to discontinue the search.");
                return true;
            }
            optInvRowVec = optIndexShare[0];
            optInvColVec = optIndexShare[1];

            int neighbours;
            // to verify if row and column vector created by client is correct
            clientVerification = false;
            if (clientVerification) {
                task22();
                // with test 5
                // long[] result = new long[hashBlockCount + 2 + optInvRowVec.length +
                // optInvColVec.length + 1];
                // without test 5
                result = new long[dispatcher.hashBlockCount + 1]; // one to add the server label
                // for test 1
                task25();
                // sending addr data to servers
                long[] data = new long[dispatcher.hashBlockCount + 1];
                for (int i = 0; i < dispatcher.hashBlockCount; i++) {
                    data[i] = phase2AddrResult[2 + dispatcher.hashBlockCount + i];
                }
                data[data.length - 1] = (Math.floorMod(serverNumber - 2, serverCount) + 1);
                // shares data with neighbouring servers
                neighbours = 1;
                // receive data from neighbouring servers
                data = (long[]) communicateAllServers(data, neighbours);
                ;
                for (int i = 0; i < 3; i++) { // performing subtraction with its own computed hash digest of column
                                              // vector positions
                    result[i] = Math.floorMod((result[i] - data[i]), dispatcher.modValue);
                }
                result[result.length - 1] = serverNumber;
                neighbours = 2;
                communicateAllServers(result, neighbours);
                // check if hash digest on column vector send by client - one stored at server
                // in addr table is zero
                long[] hashResult = new long[dispatcher.hashBlockCount];
                long[] shares = new long[serverCount];
                for (int i = 0; i < result.length - 1; i++) {
                    for (int l = 0; l < serverCount; l++) {
                        if (l == (serverNumber - 1)) {
                            shares[l] = result[i];
                        } else {
                            shares[l] = objectsReceivedLong1D[l][i];
                        }
                    }
                    hashResult[i] = lagrangeInterpolation(shares);
                }
                boolean flag = true;
                for (int i = 0; i < dispatcher.hashBlockCount; i++) {
                    if (hashResult[i] != 0) {
                        flag = false;
                        break;
                    }
                }
                if (!flag) {
                    log.info("Client has prepared an incorrect opt inv row/col vector.");
                    sendToClient(new long[] { 0, serverNumber });
                    return true;
                }
            } else {
                // fetch file ids from the inverted index
                System.out.println("Going into task23");
                task23();
                for (int x = 0; x < 58; x++) {
                    System.out.print(dispatcher.optInv[x] + " ");
                }

                System.out.println();

            }
            // create random number shares and share with other server and compute the final
            // sum of random number
            // received from neighbouring servers
            createRandomShares(serverCount, phase);
            // perform dot product between result from column vector and add random value
            for (int i = 0; i < dispatcher.optCol; i++) {
                phase2OptInvResult[i] = ((randomSharesPhase2Sum[i] * optInvColVec[i]) % dispatcher.modValue
                        + phase2OptInvResult[i]) % dispatcher.modValue;
            }
            phase2OptInvResult[phase2OptInvResult.length - 1] = serverNumber;
            // send to client the file ids
            sendToClient(phase2OptInvResult);
        }
        return false;
    }

    /**
     * to fetch the file given file id
     *
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void phase3() throws IOException, ClassNotFoundException, NoSuchAlgorithmException, InterruptedException {
        stage = "3";

        // Waiting for client to send the data
        // reading the data sent by client
        fileVectorShare = (long[][]) clientRequestQueue.take().getPayload();

        fileRequestedCount = fileVectorShare.length - 1;
        // this is executed if client is unhappy with phase2 result send by server and
        // wants to discontinue
        if (fileVectorShare[0][0] == 0) {
            log.info("Client wishes to discontinue the search.");
            return;
        }
        {
            // check if client has access to all the keyword of the file
            long[][] verificationForServer = new long[fileRequestedCount][1 + dispatcher.checkpoint1
                    + dispatcher.hashBlockCount]; // file id
            // and
            // keyword
            // access
            // test
            verificationForServerThreadPhase3 = new long[fileRequestedCount][numThreads][1 + dispatcher.checkpoint1
                    + dispatcher.hashBlockCount];
            verificationForServerPhase3 = new long[fileRequestedCount][1 + dispatcher.checkpoint1
                    + dispatcher.hashBlockCount];
            fileIds = new long[fileRequestedCount + 1][1];
            // performing operation to get the file content and keyword list
            phase3Result = new long[fileRequestedCount + 1][dispatcher.fileLength]; // 1 more to store the label for
                                                                                    // server
            phase3ResultThread = new long[fileRequestedCount][numThreads][dispatcher.fileLength];
            System.err.println("Before entering task31");
            task31();
            System.err.println("After existing task31");
            for (int i = 0; i < fileRequestedCount; i++) { // aggregate the value over thread
                // for (int j = 0; j < numThreads; j++) {
                for (int k = 0; k < dispatcher.checkpoint1 + 1 + dispatcher.hashBlockCount; k++) {
                    verificationForServer[i][k] = (verificationForServer[i][k]
                            // + verificationForServerThreadPhase3[i][j][k])
                            + verificationForServerPhase3[i][k])
                            % dispatcher.modValue;
                    // }
                }
                fileIds[i][0] = verificationForServer[i][0];
            }
            if (clientVerification) {
                int ownRotate = (int) fileVectorShare[fileRequestedCount][0];
                int otherRotate = (int) fileVectorShare[fileRequestedCount][1];

                // verify if the file id sent is correct
                permutReorgVector = new long[dispatcher.optCol];
                System.arraycopy(phase2OptInvResult, 0, permutReorgVector, 0, dispatcher.optCol);

                System.out.println(Integer.toString(phase2OptInvResult.length) + "is the length");
                // rotate own vector
                permutReorgVector = rotateVector(0, ownRotate);
                // share with other servers and receive their data
                permutReorgVector = (long[]) communicateAllServers(permutReorgVector, true);
                // rotate data received from server
                permutReorgVector = rotateVector(1, otherRotate);
                // subtract the file ids to see if same file id is requested as in phase 2
                for (int i = 0; i < fileRequestedCount; i++) {
                    fileIds[i][0] = Math.floorMod((fileIds[i][0] -
                            permutReorgVector[i + dispatcher.hashBlockCount]), dispatcher.modValue);
                }

                long[][] ownData = binaryCompute(fileVectorShare);

                communicateAllServers(ownData);
                long[][] binaryResult = interpolate(serverVerificationShares2D, ownData, serverCount);

                if (!checkBinary(binaryResult)) {
                    log.info("Client has prepared an incorrect file vector");
                    sendToClient(new long[][] { { 0 }, { serverNumber } });
                    return;
                }
            }
            // to get the keyword for a file
            long[][] result1 = new long[fileRequestedCount + 1][dispatcher.checkpoint1];
            for (int i = 0; i < verificationForServer.length; i++) {
                for (int j = dispatcher.hashBlockCount + 1; j < verificationForServer[0].length; j++) {
                    result1[i][j - dispatcher.hashBlockCount - 1] = verificationForServer[i][j];
                }
            }
            // sending to client
            result1[result1.length - 1] = new long[] { Math.floorMod(serverNumber - 2, serverCount) + 1 };
            sendToClient(result1);

            // received keyword list in ACT size and reduced degrees
            long[][] received = (long[][]) clientRequestQueue.take().getPayload();

            // check if the file keyword list returned is correct
            long[][] hashResult = new long[fileRequestedCount + 1][dispatcher.hashBlockCount];
            for (int i = 0; i < fileRequestedCount; i++) {
                long[] result = new long[dispatcher.hashBlockCount];
                for (int k = 0; k < dispatcher.keywordCount; k++) {
                    for (int j = 0; j < dispatcher.hashBlockCount; j++) {
                        result[j] = (result[j]
                                + (received[i][k] * dispatcher.optInvHashValues[k + 1][j]) % dispatcher.modValue)
                                % dispatcher.modValue;
                    }
                }
                for (int j = 0; j < dispatcher.hashBlockCount; j++) {
                    hashResult[i][j] = Math.floorMod((result[j] - verificationForServer[i][j + 1]),
                            dispatcher.modValue);
                }
            }
            hashResult[hashResult.length - 1][0] = (Math.floorMod(serverNumber - 2, serverCount) + 1);

            // communicate to the servers
            communicateAllServers(hashResult);
            long[][] hashRes = interpolate(serverVerificationShares2D, hashResult, serverCount);
            for (int i = 0; i < fileRequestedCount; i++) {
                for (int j = 0; j < dispatcher.hashBlockCount; j++) {
                    if (hashRes[i][j] != 0) {
                        // log.info("Client has prepared an incorrect file vector");
                        // removeTime.add(Instant.now());
                        // comTime.add(Instant.now());
                        // sendToClient(new long[][]{{0}, {serverNumber}});
                        // comTime.add(Instant.now());
                        // perServerTime.add(Helper.calculateSendToServerTime(comTime, waitTime, 1));
                        // comTime = new ArrayList<>();
                        // waitTime = new ArrayList<>();
                        // removeTime.add(Instant.now());
                        // return;
                    }
                }
            }

            // to check if client has access on all keywords
            BigInteger[] temp1 = new BigInteger[fileRequestedCount];
            for (int i = 0; i < fileRequestedCount; i++) {
                temp1[i] = BigInteger.valueOf(0);
            }

            for (int i = 0; i < fileRequestedCount; i++) {
                for (int k = 0; k < dispatcher.keywordCount; k++) {
                    temp1[i] = (temp1[i].add((BigInteger.valueOf(received[i][k])
                            .multiply(dispatcher.act[clientId][k])).mod(dispatcher.modValueBig)))
                            .mod(dispatcher.modValueBig);
                }
            }
            StringBuilder[] temp3 = new StringBuilder[fileRequestedCount];
            byte[][] result = new byte[fileRequestedCount + 1][];
            for (int i = 0; i < fileRequestedCount; i++) {
                temp3[i] = new StringBuilder("");
                temp3[i].append(temp1[i]).append(",");
                result[i] = temp3[i].toString().getBytes(StandardCharsets.UTF_8);
            }
            result[result.length - 1] = String.valueOf(Math.floorMod(serverNumber - 2, serverCount) + 1)
                    .getBytes(StandardCharsets.UTF_8);
            communicateAllServers(result);
            // check if the interpolated value yields desired value
            BigInteger[] testResultBig = new BigInteger[fileRequestedCount];
            BigInteger[] sharesBig = new BigInteger[serverCount];
            for (int i = 0; i < fileRequestedCount; i++) {
                for (int l = 0; l < serverCount; l++) {
                    if (l == (Math.floorMod(serverNumber - 2, serverCount))) {
                        sharesBig[l] = temp1[i];
                    } else {
                        sharesBig[l] = new BigInteger(objectsReceivedString2D[l][i][0]);
                    }
                }
                testResultBig[i] = lagrangeInterpolation(sharesBig);
            }

            for (int i = 0; i < fileRequestedCount; i++) {
                if (!testResultBig[i].equals(BigInteger.valueOf(0))) {
                    System.out.println("FAILED: Expected 0, got " + testResultBig[i]);

                    // Print the components that led to this
                    System.out.println("temp1[" + i + "] = " + temp1[i]);
                    for (int l = 0; l < serverCount; l++) {
                        if (l == (Math.floorMod(serverNumber - 2, serverCount))) {
                            System.out.println("Server " + (l + 1) + " share: " + temp1[i]);
                        } else {
                            System.out.println("Server " + (l + 1) + " share: " +
                                    new BigInteger(objectsReceivedString2D[l][i][0]));
                        }
                    }
                }
            }

            // Need to edit here
            boolean flag = true;
            for (int i = 0; i < fileRequestedCount; i++) {
                if (!(testResultBig[i].equals(BigInteger.valueOf(0)))) {
                    flag = false;
                    break;
                }
            }

            if (!flag) {
                log.info(
                        "Client has prepared an incorrect file vector Or has no access on all keywords of requested file.");
                sendToClient(new long[][] { { 0 }, { serverNumber } });
                return;
            }
        }
        // to fetch file content
        System.err.println("Before entering task32");
        task32();
        System.err.println("After exiting task32");
        phase3Result[phase3Result.length - 1] = new long[] { (Math.floorMod(serverNumber - 2, serverCount) + 1) };
        sendToClient(phase3Result);
    }

}
