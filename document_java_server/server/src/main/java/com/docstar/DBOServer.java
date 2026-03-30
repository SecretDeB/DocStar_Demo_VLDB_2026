package com.docstar;

import lombok.Data;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import com.docstar.model.dto.FileShare;
import com.docstar.model.dto.FilesDataStructureShares;
import com.docstar.model.dto.ResizeOptRequest;
import com.docstar.model.dto.SparseDelta;

@Data
public class DBOServer {

    private Properties properties;
    private final Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private Object objects;
    private List<Object> objectsList;
    private BigInteger searchKeywordShare;
    private int clientId;
    private long[] searchKeywordVectorShare;

    // to store number of files
    private long[][] fileVectorShare;

    // to store the result for phase1
    private StringBuilder[] phase1Result;

    private BigInteger[] verificationForServer;

    // for phase 3 verification phase
    private long[][][] verificationForServerThreadPhase3;

    private int numThreads;
    private int serverCount;
    // the mod value
    private final long modValue = Constant.getModParameter();
    // the mod value for BigInteger
    private final BigInteger modValueBig = Constant.getModBigParameter();
    private String stage;
    private String stageLabel;
    private long seedServer;
    private int fileRequestedCount;
    private String[] newAccess;

    // Time tracking variables
    private ArrayList<Instant> removeTime;
    private ArrayList<Instant> comTime = new ArrayList<>();
    private ArrayList<Instant> waitTime = new ArrayList<>();
    private ArrayList<Double> perServerTime;

    // Temporary Computation Variables
    private long[] newOptInv;
    private long[][] newAddr;
    private byte[][] actUpdate;
    private long[][][] phase3ResultThread;

    // to store the server port
    private int serverPort;
    // to store the client IP
    private String dboIP;
    // to store the client port
    private int dboPort;
    private ObjectOutputStream dboObjectOutputStream;

    private long[] phase2AddrResult;
    private long[][] addrUpdate;
    private long[][] optIndexShare;
    private long[] optInvRowVec;
    private long[] optInvColVec;
    private long[] phase2OptInvResult;
    private long[][] optInvServerVerification;
    private long[][] optInvHashValues;

    private final ServerDispatcher serverDispatcher;

    public DBOServer(ServerDispatcher serverDispatcher, Properties commonProperties) {
        this.serverDispatcher = serverDispatcher;
        // reading the properties file for server

        // reading the commonProperties file for client

        dboPort = Integer.parseInt(commonProperties.getProperty("dboPort"));
        dboIP = commonProperties.getProperty("dboIP");
        numThreads = Integer.parseInt(commonProperties.getProperty("numThreads"));
        serverCount = serverDispatcher.getServerCount();
        cleanUp();
    }

    // multithreaded across number of files
    private void task31(int threadNum) {

        int batchSize = (int) Math.ceil((serverDispatcher.getFileCount() + 1) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > (serverDispatcher.getFileCount() + 1)) {
            end = (serverDispatcher.getFileCount() + 1);
        }

        for (int i = 0; i < fileRequestedCount; i++) {
            for (int j = start; j < end; j++) {
                long[] temp = serverDispatcher.getFileKeyword()[j];
                for (int k = 0; k < serverDispatcher.getKeywordCount() + 1; k++) {
                    verificationForServerThreadPhase3[i][threadNum - 1][k] = Helper
                            .mod(verificationForServerThreadPhase3[i][threadNum - 1][k]
                                    + Helper.mod(temp[k] * fileVectorShare[i][j]));

                }
                temp = serverDispatcher.getFiles()[i];
                for (int k = 0; k < temp.length; k++) {
                    phase3ResultThread[i][threadNum - 1][k] = Helper.mod(phase3ResultThread[i][threadNum - 1][k]
                            + Helper.mod(fileVectorShare[i][j] * temp[k]));
                }
            }
        }

    }

    // multithreaded across the number of files
    private void task32(int threadNum) {

        int batchSize = (int) Math.ceil((serverDispatcher.getFileCount() + 1) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.getFileCount() + 1) {
            end = serverDispatcher.getFileCount() + 1;
        }

        for (int m = 0; m < fileRequestedCount; m++) {
            for (int i = start; i < end; i++) {
                long[] temp = serverDispatcher.getFiles()[i];
                for (int j = 0; j < temp.length; j++) {
                    phase3ResultThread[m][threadNum - 1][j] = Helper.mod(phase3ResultThread[m][threadNum - 1][j]
                            + Helper.mod(fileVectorShare[m][i] * temp[j]));
                }
            }
        }
    }

    // multithreaded across the number of cols in opt inverted index
    private void task22(int threadNum) throws NoSuchAlgorithmException {

        int batchSize = (int) Math.ceil((serverDispatcher.getOptCol()) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.getOptCol()) {
            end = serverDispatcher.getOptCol();
        }

        for (int i = 0; i < serverDispatcher.optRow; i++) {
            for (int j = start; j < end; j++) {
                for (int k = 0; k < Constant.getHashBlockCount(); k++) {
                    optInvServerVerification[j][k] = Helper.mod(optInvServerVerification[j][k] +
                            Helper.mod(optInvHashValues[i * optInvColVec.length + j][k] * optInvRowVec[i]));
                }

                phase2OptInvResult[j] = Helper.mod(phase2OptInvResult[j] +
                        Helper.mod(serverDispatcher.optInv[i * optInvColVec.length + j] * optInvRowVec[i]));
            }
        }
    }

    // multithreaded across number of keywords
    private void task24(int threadNum) {

        int batchSize = (int) Math.ceil((serverDispatcher.addr[0].length + 6) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > (serverDispatcher.addr[0].length + 2)) {
            end = (serverDispatcher.addr[0].length + 2);
        }

        BigInteger[] keywords = serverDispatcher.act[0];
        BigInteger[] access = serverDispatcher.act[clientId];

        verificationForServer[0] = BigInteger.valueOf(0);
        verificationForServer[1] = BigInteger.valueOf(0);
        for (int i = 0; i < serverDispatcher.keywordCount; i++) {

            long[] temp = serverDispatcher.addr[i];
            for (int j = start; j < end; j++) {
                if (j < serverDispatcher.addr[0].length) {
                    phase2AddrResult[j] = Helper.mod(phase2AddrResult[j] +
                            Helper.mod(temp[j] * searchKeywordVectorShare[i]));
                } else if (j == serverDispatcher.addr[0].length) {
                    verificationForServer[0] = Helper
                            .mod(Helper.mod(keywords[i].multiply(BigInteger.valueOf(searchKeywordVectorShare[i])))
                                    .add(verificationForServer[0]));
                } else if (j == serverDispatcher.addr[0].length + 1) {
                    verificationForServer[1] = Helper
                            .mod(Helper.mod(access[i].multiply(BigInteger.valueOf(searchKeywordVectorShare[i])))
                                    .add(verificationForServer[1]));
                }
            }
        }
    }

    // multithreaded across number of keywords
    private void task26(int threadNum) {
        int batchSize = (int) Math.ceil((serverDispatcher.keywordCount) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }

        for (int i = start; i < end; i++) {
            // System.out.println("i:" + i);
            for (int j = 0; j < 2 + (2 * Constant.getHashBlockCount()); j++) {
                serverDispatcher.addr[i][j] = Helper.mod(serverDispatcher.addr[i][j] + newAddr[i][j]);
                // if(i==0)
                // System.out.print(addr[i][j] + " ");
            }
        }
        // System.out.println();

    }

    private void updateAddrData() throws InterruptedException, IOException, ClassNotFoundException {

        stage = "2";

        // Waiting for client to send the data

        newAddr = (long[][]) serverDispatcher.dboQueue.take().getPayload();

        if (newAddr[0][0] != 0) { // if free slot present
            // computing phase 1 operation on ACT list
            stageLabel = "6";
            createThreads(stage, stageLabel);
        } else { // if free slot absent

            long[][] addrWithLabel = new long[serverDispatcher.addr.length + 1][serverDispatcher.addr[0].length];

            // Copy addr data
            System.arraycopy(serverDispatcher.addr, 0, addrWithLabel, 0, serverDispatcher.addr.length);

            // Add server label in last row, first column
            addrWithLabel[serverDispatcher.addr.length][0] = (Math.floorMod(serverDispatcher.serverNumber - 2,
                    serverCount) + 1);

            // Send to client
            sendToClient(addrWithLabel);

            long[][] addrTemp = (long[][]) serverDispatcher.dboQueue.take().getPayload();

            System.arraycopy(addrTemp, 0, serverDispatcher.addr, 0, addrTemp.length);
        }

    }

    // multithreaded across number of keywords
    private void task12(int threadNum) {
        int batchSize = (int) Math.ceil(serverDispatcher.keywordCount / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }

        BigInteger[] keywords = serverDispatcher.act[0];
        BigInteger[] access = serverDispatcher.act[clientId];
        BigInteger temp;

        long prgServer;
        Random seedGenerator = new Random(seedServer);
        for (int i = start; i < end; i++) {
            prgServer = seedGenerator.nextLong(Constant.getMaxRandomBound() -
                    Constant.getMinRandomBound()) + Constant.getMinRandomBound();
            temp = Helper.mod((Helper.mod(Helper.mod(keywords[i].subtract(searchKeywordShare)).add(access[i])))
                    .multiply(BigInteger.valueOf(prgServer)));
            phase1Result[threadNum - 1].append(temp).append(",");
        }
    }

    private void sendToClient(Object data) {
        // This sends data back to the DBO admin tool (your original DBO.java)
        try {
            // System.out.println("Sending to client..." + data.getClass());
            dboObjectOutputStream.writeObject(data);
            dboObjectOutputStream.flush();

        } catch (IOException e) {
            log.log(Level.SEVERE, "DBO Service failed to send data to client", e);
        }
    }

    private class ThreadTask implements Runnable {
        private int threadNum;
        private String stage;
        private String stageLabel;

        public ThreadTask(int threadNum, String stage, String stageLabel) {
            this.threadNum = threadNum;
            this.stage = stage;
            this.stageLabel = stageLabel;
        }

        @Override
        public void run() {
            if (stage.equals("0")) {
                if (stageLabel.equals("1"))
                    task01(threadNum);
                else if (stageLabel.equals("2")) {
                    task02(threadNum);
                }
            } else if (stage.equals("1")) {
                if (stageLabel.equals("1"))
                    task11(threadNum);
                else if (stageLabel.equals("2"))
                    task12(threadNum);
                else if (stageLabel.equals("3"))
                    task13(threadNum);
                else if (stageLabel.equals("4"))
                    task14(threadNum);
            } else if (stage.equals("2")) {
                if (stageLabel.equals("1")) {
                    task21(threadNum);
                } else if (stageLabel.equals("2")) {
                    try {
                        task22(threadNum);
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                } else if (stageLabel.equals("3")) {
                    task23(threadNum);
                } else if (stageLabel.equals("4")) {
                    task24(threadNum);
                } else if (stageLabel.equals("5")) {
                    task25(threadNum);
                } else if (stageLabel.equals("6")) {
                    task26(threadNum);
                }
            } else if (stage.equals("3")) {
                if (stageLabel.equals("1")) {
                    task31(threadNum);
                } else if (stageLabel.equals("2")) {
                    task32(threadNum);
                }
            }

        }

    }

    private void createThreads(String stage, String stageLabel) {
        List<Thread> threadList = new ArrayList<>();

        // Create threads and add them to threadlist
        int threadNum;
        for (int i = 0; i < numThreads; i++) {
            threadNum = i + 1;
            threadList.add(new Thread(new ThreadTask(threadNum, stage, stageLabel), "Thread" + threadNum));
        }

        // Start all threads
        for (int i = 0; i < numThreads; i++) {
            threadList.get(i).start();
        }

        // Wait for all threads to finish
        for (Thread thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * To perform cleanup tasks in course of program execution
     */
    public void cleanUp() {
        // re-initializing the list to hold values received from server
        objectsList = Collections.synchronizedList(new ArrayList<>());
    }

    // multithreaded across the number of rows in opt inverted index
    private void task23(int threadNum) {
        int batchSize = (int) Math.ceil((serverDispatcher.optCol) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.optCol) {
            end = serverDispatcher.optCol;
        }

        for (int i = 0; i < this.serverDispatcher.optRow; i++) {
            for (int j = start; j < end; j++) {
                // System.out.println("i:"+i+" j:"+j);
                // System.out.println("optInv[i * optCol + j]:"+optInv[i * optCol + j]);
                phase2OptInvResult[j] = (phase2OptInvResult[j] +
                        (serverDispatcher.optInv[i * /* optInvColVec.length */this.serverDispatcher.optCol + j]
                                * optInvRowVec[i])
                                % modValue)
                        % modValue;
            }
        }

    }

    public void getOptValues() throws InterruptedException {
        // performing operation to get the file ids
        // Waiting for client to send the position and count of data to be retrieved
        // from opt tabel
        Object serverDispatcherData = serverDispatcher.dboQueue.take().getPayload();
        if (serverDispatcherData instanceof String) {
            System.out.println("Received String: " + serverDispatcherData);
        }
        optIndexShare = (long[][]) serverDispatcherData;// serverDispatcher.dboQueue.take().getPayload();

        optInvRowVec = optIndexShare[0];
        phase2OptInvResult = new long[serverDispatcher.optCol + 1];
        stageLabel = "3";
        createThreads(stage, stageLabel);
        phase2OptInvResult[phase2OptInvResult.length - 1] = serverDispatcher.serverNumber;
        sendToClient(phase2OptInvResult);
    }

    // multithreaded across number of keywords
    private void task25(int threadNum) {
        int batchSize = (int) Math.ceil((serverDispatcher.optInv.length) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > (serverDispatcher.optInv.length)) {
            end = (serverDispatcher.optInv.length);
        }

        for (int j = start; j < end; j++) {
            serverDispatcher.optInv[j] = Helper.mod(serverDispatcher.optInv[j] + newOptInv[j]);
            // if(j%optCol==0 && j!=0)
            // System.out.println();;
            // System.out.print(optInv[j] + " ");

        }

    }

    /**
     * to compute hash digest of each cell of inverted index
     *
     * @throws NoSuchAlgorithmException
     */
    public void calOptInvHashValues() {
        int totalSlots = serverDispatcher.optInv.length;
        System.out.println("Generating index hashes for " + totalSlots + " slots using parallel processing...");

        serverDispatcher.optInvHashValues = new long[totalSlots][serverDispatcher.hashBlockCount];

        // Pre-calculate constants
        final int hashBlockCount = serverDispatcher.hashBlockCount;
        final int hashBlockSize = serverDispatcher.hashBlockSize;
        final BigInteger modulus = serverDispatcher.modValueBig;

        // Use ForkJoinPool with controlled parallelism
        int processors = Runtime.getRuntime().availableProcessors();
        ForkJoinPool customThreadPool = new ForkJoinPool(processors);

        try {
            customThreadPool.submit(() -> {
                // Process in batches to improve cache locality
                int batchSize = 1000;
                IntStream.range(0, (totalSlots + batchSize - 1) / batchSize)
                        .forEach(batchIndex -> {
                            // Thread-local MessageDigest (reuse for entire batch)
                            MessageDigest md;
                            try {
                                md = MessageDigest.getInstance("SHA-1");
                            } catch (NoSuchAlgorithmException e) {
                                throw new RuntimeException(e);
                            }

                            // Pre-allocate reusable byte array
                            byte[] indexBytes = new byte[12]; // Enough for most integers

                            int start = batchIndex * batchSize;
                            int end = Math.min(start + batchSize, totalSlots);

                            for (int i = start; i < end; i++) {
                                try {
                                    // OPTIMIZATION 1: Reuse MessageDigest
                                    md.reset();

                                    // OPTIMIZATION 2: Avoid String creation for index
                                    int len = intToBytes(i, indexBytes);
                                    md.update(indexBytes, 0, len);
                                    byte[] digest = md.digest();

                                    // OPTIMIZATION 3: Use more efficient modulo
                                    BigInteger hashValue = new BigInteger(1, digest);
                                    if (modulus != null) {
                                        hashValue = hashValue.mod(modulus);
                                    }

                                    // OPTIMIZATION 4: Avoid string conversion - work with BigInteger directly
                                    extractHashBlocksFromDigest(digest, serverDispatcher.optInvHashValues[i],
                                            hashBlockCount, hashBlockSize);

                                } catch (Exception e) {
                                    System.err.println("Error processing index " + i + ": " + e.getMessage());
                                }
                            }
                        });
            }).get();
        } catch (Exception e) {
            throw new RuntimeException("Hash generation failed", e);
        } finally {
            customThreadPool.shutdown();
            customThreadPool.close();
        }

        System.out.println("Index hash generation complete.");
    }

    // Helper method to convert int to bytes efficiently
    private static int intToBytes(int value, byte[] buffer) {
        int len = 0;
        if (value == 0) {
            buffer[0] = '0';
            return 1;
        }

        boolean negative = value < 0;
        if (negative)
            value = -value;

        int temp = value;
        while (temp > 0) {
            len++;
            temp /= 10;
        }

        int pos = len - 1;
        while (value > 0) {
            buffer[pos--] = (byte) ('0' + (value % 10));
            value /= 10;
        }

        if (negative) {
            System.arraycopy(buffer, 0, buffer, 1, len);
            buffer[0] = '-';
            len++;
        }

        return len;
    }

    private static void extractHashBlocksFromDigest(
            byte[] digest, long[] output, int blockCount, int blockSize) {

        int bitPos = 0;
        int bitsPerBlock = (int) (Math.log10(Math.pow(2, blockSize)) / Math.log10(2));

        for (int i = 0; i < blockCount; i++) {
            long value = 0;
            for (int b = 0; b < bitsPerBlock; b++) {
                int byteIndex = bitPos >> 3;
                int bitIndex = bitPos & 7;
                value = (value << 1) |
                        ((digest[byteIndex] >> (7 - bitIndex)) & 1);
                bitPos++;
            }
            output[i] = value;
        }
    }

    // SERVER SIDE: Send share label with data
    public void getAllClientAccess() {
        System.out.println("\n=== SERVER " + serverDispatcher.serverNumber + " GET ALL CLIENT ACCESS ===");

        serverCount = 3;

        int actualClientCount = serverDispatcher.act.length - 2;
        System.out.println("Total clients: " + actualClientCount);
        System.out.println("Number of keywords: " + serverDispatcher.keywordCount);

        // Calculate server's share label (used in Lagrange interpolation)
        int shareLabel = Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1;

        System.out.println("Server number: " + serverDispatcher.serverNumber);
        System.out.println("Share label: " + shareLabel);

        // Prepare response: [shareLabel, client1_row, client2_row, ...]
        // We'll serialize as: shareLabel|client1_data|client2_data|...

        BigInteger[][] allClientRows = new BigInteger[actualClientCount][];
        for (int c = 0; c < actualClientCount; c++) {
            allClientRows[c] = serverDispatcher.act[2 + c];
        }

        System.out.println("Sending " + actualClientCount + " client rows");

        try {
            // Create response with label
            Object[] response = new Object[2];
            response[0] = shareLabel; // First element is the share label
            response[1] = allClientRows; // Second element is the data

            System.out.flush();
            sendToClient(response);
            System.out.println("Successfully sent to client (label=" + shareLabel + ")");
        } catch (Exception e) {
            System.err.println("ERROR sending to client:");
            e.printStackTrace();
        }
    }

    /**
     * Handle adding a new keyword to the system
     */
    public void addNewKeyword() throws InterruptedException, IOException, ClassNotFoundException {
        System.out.println("\n=== SERVER " + serverDispatcher.serverNumber + " ADD NEW KEYWORD ===");

        stage = "1";

        // Step 1: Client calls getKeywordIndex() - respond normally
        if (serverDispatcher.act[0].length > 0) {
            getKeywordIndex();
        }

        // Step 2: Check if client aborted or continuing
        Object nextData = serverDispatcher.dboQueue.take().getPayload();

        if (nextData instanceof byte[] && ((byte[]) nextData).length == 1 && ((byte[]) nextData)[0] == 0) {
            System.out.println("Keyword exists - aborting add operation");
            return;
        }

        // ===== STEP 2E: RECEIVE AND APPEND ACT DATA =====
        System.out.println("\n=== APPENDING TO ACT TABLE ===");

        byte[] actUpdate = (byte[]) nextData;
        String actData = new String(actUpdate, StandardCharsets.UTF_8);
        String[] parts = actData.split(",");

        System.out.println("Received ACT data: " + parts.length + " values");

        // Append to each row efficiently
        int oldLength = serverDispatcher.act[0].length;

        for (int i = 0; i < serverDispatcher.act.length; i++) {
            // Use Arrays.copyOf to expand array by 1
            serverDispatcher.act[i] = Arrays.copyOf(serverDispatcher.act[i], oldLength + 1);

            // Set new value
            if (i == 0) {
                serverDispatcher.act[i][oldLength] = new BigInteger(parts[0]); // keyword
            } else if (i == 1) {
                serverDispatcher.act[i][oldLength] = new BigInteger(parts[1]); // position
            } else {
                serverDispatcher.act[i][oldLength] = new BigInteger(parts[i]); // access
            }
        }

        serverDispatcher.keywordCount = serverDispatcher.act[0].length;

        System.out.println("ACT table updated: " + oldLength + " → " + serverDispatcher.act[0].length + " keywords");

        // Step 2: Receive ADDR update (startPos, count, hashes)

        System.out.println("Sending optRow and last ADDR to client");

        long[] response;
        if (serverDispatcher.addr.length == 0) {
            // First keyword - ADDR array is empty
            System.out.println("First keyword - sending default ADDR");

            int addrLength = 2 + 2 * Constant.getHashBlockCount();
            response = new long[2 + addrLength];

            response[0] = serverDispatcher.optRow; // optRow = 1

            // Fill with zeros (default ADDR)
            for (int i = 1; i < response.length - 1; i++) {
                response[i] = 0;
            }

            // Server label at end
            response[response.length - 1] = (Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1);

        } else {
            // Not first keyword - send actual last ADDR
            int lastKeywordIndex = serverDispatcher.addr.length - 1;

            System.out.println("Sending last ADDR from index " + lastKeywordIndex);

            response = new long[2 + serverDispatcher.addr[0].length]; // ← Now safe!

            response[0] = serverDispatcher.optRow;
            System.arraycopy(serverDispatcher.addr[lastKeywordIndex], 0, response, 1,
                    serverDispatcher.addr[0].length);
            response[response.length - 1] = (Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1);
        }

        sendToClient(response);

        long[] addrUpdate = (long[]) serverDispatcher.dboQueue.take().getPayload();

        System.out.println("Received ADDR data: " + Arrays.toString(addrUpdate));
        System.out.println(" startPos share: " + addrUpdate[0]);
        System.out.println(" count share: " + addrUpdate[1]);

        // Append new row to ADDR
        int oldRows = serverDispatcher.addr.length;
        serverDispatcher.addr = Arrays.copyOf(serverDispatcher.addr, oldRows + 1);
        serverDispatcher.addr[oldRows] = addrUpdate;

        System.out.println("ADDR table updated: " + oldRows + " → " + serverDispatcher.addr.length + " rows");

        System.out.println("\n NEW KEYWORD ADDED TO SERVER " + serverDispatcher.serverNumber + " ");

        // For now, just acknowledge receipt
        System.out.println("New keyword data received");
    }

    public void addNewKeywordsBatch() throws InterruptedException, IOException, ClassNotFoundException {
        System.out.println("\n=== SERVER " + serverDispatcher.serverNumber + " ADD NEW KEYWORDS BATCH ===");

        stage = "1";

        // Step 1: Client calls getKeywordIndex() for each keyword - respond normally
        // (This happens in the loop in DBO before calling this)

        // Step 2: Receive batch ACT data
        Object nextData = serverDispatcher.dboQueue.take().getPayload();

        if (nextData instanceof byte[] && ((byte[]) nextData).length == 1 && ((byte[]) nextData)[0] == 0) {
            System.out.println("One or more keywords exist - aborting batch add operation");
            return;
        }

        // Parse batch data
        byte[] actBatchData = (byte[]) nextData;
        String batchStr = new String(actBatchData, StandardCharsets.UTF_8);
        String[] batches = batchStr.split("\\|");

        int numKeywords = Integer.parseInt(batches[0]);
        System.out.println("Receiving batch of " + numKeywords + " keywords");

        // ===== HANDLE EMPTY SYSTEM CASE =====
        boolean isSystemEmpty = (serverDispatcher.act == null || serverDispatcher.act.length == 0 ||
                serverDispatcher.act[0] == null || serverDispatcher.act[0].length == 0);

        if (isSystemEmpty) {
            System.out.println("⚠ ACT table is empty - initializing for first keywords");

            // Initialize ACT table structure
            // Rows: [keywords, positions, client1_access, client2_access, ...]
            int numRows = 2 + serverDispatcher.clientCount;
            serverDispatcher.act = new BigInteger[numRows][numKeywords];

            // Process each keyword
            for (int kwIdx = 0; kwIdx < numKeywords; kwIdx++) {
                String[] parts = batches[kwIdx + 1].split(",");

                System.out.println("\n=== ADDING KEYWORD " + (kwIdx + 1) + " (COLD START) ===");
                System.out.println("Received ACT data: " + parts.length + " values");

                // Row 0: keyword
                serverDispatcher.act[0][kwIdx] = new BigInteger(parts[0]);

                // Row 1: position
                serverDispatcher.act[1][kwIdx] = new BigInteger(parts[1]);

                // Rows 2+: client access
                for (int clientIdx = 0; clientIdx < serverDispatcher.clientCount; clientIdx++) {
                    serverDispatcher.act[2 + clientIdx][kwIdx] = new BigInteger(parts[2 + clientIdx]);
                }

                System.out.println("Keyword " + (kwIdx + 1) + " added to ACT (cold start)");
            }

            serverDispatcher.keywordCount = numKeywords;
            System.out.println("\n ACT table initialized with " + numKeywords + " keywords");
            System.out.println("New keywordCount: " + serverDispatcher.keywordCount);

            if (serverDispatcher.serverNumber < 4) {
                // ===== INITIALIZE ADDR TABLE =====
                System.out.println("\n=== INITIALIZING ADDR TABLE ===");

                // Initialize optRow if needed
                if (serverDispatcher.optRow == 0) {
                    serverDispatcher.optRow = 1; // Start with at least 1 row
                    System.out.println("Initialized optRow: " + serverDispatcher.optRow);
                }

                // Send optRow and "last ADDR" (which is zeros for empty system)
                System.out.println("Sending optRow and empty last ADDR to client");

                // For empty system, send optRow + zeros for last ADDR
                int addrEntryLength = 2 + 2 * Constant.getHashBlockCount();
                long[] response = new long[2 + addrEntryLength];
                response[0] = serverDispatcher.optRow;

                // response[1..addrEntryLength] are already 0 (default)
                // Last element is server label
                response[response.length
                        - 1] = (Math.floorMod(serverDispatcher.serverNumber - 2, serverDispatcher.serverCount)
                                + 1);

                sendToClient(response);

                // Initialize ADDR array
                serverDispatcher.addr = new long[numKeywords][addrEntryLength];

                // Receive ADDR updates for each keyword
                for (int kwIdx = 0; kwIdx < numKeywords; kwIdx++) {
                    long[] addrUpdate = (long[]) serverDispatcher.dboQueue.take().getPayload();

                    System.out.println("\nReceived ADDR data for keyword " + (kwIdx + 1));
                    System.out.println(" startPos share: " + addrUpdate[0]);
                    System.out.println(" count share: " + addrUpdate[1]);

                    serverDispatcher.addr[kwIdx] = addrUpdate;

                    System.out.println("ADDR initialized for keyword " + (kwIdx + 1));
                }

                // ✅ RECEIVE DIMENSION UPDATE FROM DBO
                System.out.println("\n=== WAITING FOR DIMENSION UPDATE ===");
                int[] newDimensions = (int[]) serverDispatcher.dboQueue.take().getPayload();
                int newOptRow = newDimensions[0];
                int newOptCol = newDimensions[1];

                System.out.println("Received dimension update:");
                System.out.println(" Old optRow: " + serverDispatcher.optRow);
                System.out.println(" New optRow: " + newOptRow);
                System.out.println(" optCol: " + newOptCol);

                serverDispatcher.optRow = newOptRow;
                serverDispatcher.optCol = newOptCol;

                // ✅ INITIALIZE optInv with correct size
                int requiredSize = serverDispatcher.optRow * serverDispatcher.optCol;
                if (serverDispatcher.optInv == null || serverDispatcher.optInv.length < requiredSize) {
                    System.out.println("Initializing optInv: " + requiredSize + " slots");
                    serverDispatcher.optInv = new long[requiredSize];
                }

                System.out.println("Dimensions set: optRow=" + serverDispatcher.optRow +
                        ", optCol=" + serverDispatcher.optCol);
                System.out.println("\n SYSTEM INITIALIZED WITH " + numKeywords + " KEYWORDS ");
            }
        } else {
            // ===== NORMAL CASE: APPEND TO EXISTING ACT TABLE =====
            System.out.println("ACT table exists - appending keywords");

            // Process each keyword
            for (int kwIdx = 0; kwIdx < numKeywords; kwIdx++) {
                String[] parts = batches[kwIdx + 1].split(",");

                System.out.println("\n=== APPENDING KEYWORD " + (kwIdx + 1) + " TO ACT TABLE ===");
                System.out.println("Received ACT data: " + parts.length + " values");

                // Append to each row of ACT
                int oldLength = serverDispatcher.act[0].length;

                System.out.println("ACT table has " + serverDispatcher.act.length + " rows");

                for (int i = 0; i < serverDispatcher.act.length; i++) {
                    serverDispatcher.act[i] = Arrays.copyOf(serverDispatcher.act[i], oldLength + 1);

                    if (i == 0) {
                        serverDispatcher.act[i][oldLength] = new BigInteger(parts[0]); // keyword
                    } else if (i == 1) {
                        serverDispatcher.act[i][oldLength] = new BigInteger(parts[1]); // position
                    } else {
                        serverDispatcher.act[i][oldLength] = new BigInteger(parts[i]); // access
                    }
                }

                System.out.println("Keyword " + (kwIdx + 1) + " added to ACT");
            }

            // Update keyword count
            serverDispatcher.keywordCount = serverDispatcher.act[0].length;
            System.out.println("\n ACT table updated with " + numKeywords + " keywords");
            System.out.println("New keywordCount: " + serverDispatcher.keywordCount);

            if (serverDispatcher.serverNumber < 4) {
                // Send optRow and last ADDR to client
                System.out.println("Sending optRow and last ADDR to client");

                int lastKeywordIndex = serverDispatcher.addr.length - 1;
                long[] response = new long[2 + serverDispatcher.addr[0].length];
                response[0] = serverDispatcher.optRow;

                System.arraycopy(serverDispatcher.addr[lastKeywordIndex], 0, response, 1,
                        serverDispatcher.addr[0].length);
                response[response.length
                        - 1] = (Math.floorMod(serverDispatcher.serverNumber - 2, serverDispatcher.serverCount)
                                + 1);

                sendToClient(response);

                // Receive ADDR updates for each keyword
                for (int kwIdx = 0; kwIdx < numKeywords; kwIdx++) {
                    long[] addrUpdate = (long[]) serverDispatcher.dboQueue.take().getPayload();

                    System.out.println("\nReceived ADDR data for keyword " + (kwIdx + 1));
                    System.out.println(" startPos share: " + addrUpdate[0]);
                    System.out.println(" count share: " + addrUpdate[1]);

                    // Append new row to ADDR
                    int oldRows = serverDispatcher.addr.length;
                    serverDispatcher.addr = Arrays.copyOf(serverDispatcher.addr, oldRows + 1);
                    serverDispatcher.addr[oldRows] = addrUpdate;

                    System.out.println("ADDR updated for keyword " + (kwIdx + 1));
                }

                // ✅ RECEIVE DIMENSION UPDATE FROM DBO
                System.out.println("\n=== WAITING FOR DIMENSION UPDATE ===");
                int[] newDimensions = (int[]) serverDispatcher.dboQueue.take().getPayload();
                int newOptRow = newDimensions[0];
                int newOptCol = newDimensions[1];

                System.out.println("Received dimension update:");
                System.out.println(" Old optRow: " + serverDispatcher.optRow);
                System.out.println(" New optRow: " + newOptRow);
                System.out.println(" optCol: " + newOptCol);

                // ✅ UPDATE DIMENSIONS
                serverDispatcher.optRow = newOptRow;
                serverDispatcher.optCol = newOptCol;

                // ✅ EXPAND optInv if needed
                int requiredSize = serverDispatcher.optRow * serverDispatcher.optCol;
                if (serverDispatcher.optInv.length < requiredSize) {
                    System.out.println("Expanding optInv: " + serverDispatcher.optInv.length + " → " + requiredSize);
                    long[] newOptInv = new long[requiredSize];
                    System.arraycopy(serverDispatcher.optInv, 0, newOptInv, 0, serverDispatcher.optInv.length);
                    serverDispatcher.optInv = newOptInv;
                }

                System.out.println("Dimensions updated: optRow=" + serverDispatcher.optRow +
                        ", optCol=" + serverDispatcher.optCol);
            }

            System.out.println("\n BATCH KEYWORDS ADDED TO SERVER " + serverDispatcher.serverNumber + " ");
        }
    }

    public void bulkUploadFiles() throws InterruptedException {
        System.out.println("\n=== SERVER " + serverDispatcher.serverNumber + " BULK ADD FILES ===");

        // ✅ Receive combined batch data
        Object[] batchData = (Object[]) this.serverDispatcher.dboQueue.take().getPayload();

        long[][] batchContentShares = (long[][]) batchData[0];
        long[][] batchKeywordShares = (long[][]) batchData[1];

        int batchSize = batchContentShares.length;
        System.out.println("Receiving batch of " + batchSize + " files...");

        // ✅ Add files to storage
        int currentFileCount = this.serverDispatcher.fileCount;

        // Expand arrays if needed
        if (currentFileCount + batchSize > this.serverDispatcher.files.length) {
            int newCapacity = Math.max(
                    this.serverDispatcher.files.length * 2,
                    currentFileCount + batchSize);

            System.out.println("Expanding file storage: " + this.serverDispatcher.files.length +
                    " → " + newCapacity);

            this.serverDispatcher.files = Arrays.copyOf(this.serverDispatcher.files, newCapacity);
            this.serverDispatcher.fileKeyword = Arrays.copyOf(this.serverDispatcher.fileKeyword, newCapacity);
        }

        // Copy batch into main arrays
        for (int i = 0; i < batchSize; i++) {
            this.serverDispatcher.files[currentFileCount + i] = batchContentShares[i];
            this.serverDispatcher.fileKeyword[currentFileCount + i] = batchKeywordShares[i];
        }

        // Update file count
        this.serverDispatcher.fileCount += batchSize;

        System.out.println("Added " + batchSize + " files (total: " +
                this.serverDispatcher.fileCount + ")");
        System.out.println("=".repeat(60));
    }

    public void getOptDimensions() {
        int[] dimensions = { this.serverDispatcher.optRow, this.serverDispatcher.optCol };
        sendToClient(dimensions);
    }

    public void bulkInitializeSystem() throws InterruptedException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BULK INITIALIZATION - RECEIVING DATA");
        System.out.println("=".repeat(80));

        // Step 1: Receive metadata
        int[] metadata = (int[]) this.serverDispatcher.dboQueue.take().getPayload();

        int numFiles = metadata[0];
        int colSize = metadata[1];

        System.out.println("File count received: " + numFiles);
        System.out.println("Column size received: " + colSize);

        // Step 2: Receive ACT table
        BigInteger[][] actShare = (BigInteger[][]) this.serverDispatcher.dboQueue.take().getPayload();
        this.serverDispatcher.act = actShare;
        System.out.println("ACT received: " + actShare.length + " x " + actShare[0].length);

        int numKeywords = actShare[0].length;
        int clientCount = actShare.length - 2;

        if (this.serverDispatcher.serverNumber < 4) { // Servers 0, 1, 2 get ADDR and files
            // Step 3: Receive ADDR table
            long[][] addrShare = (long[][]) this.serverDispatcher.dboQueue.take().getPayload();
            this.serverDispatcher.addr = addrShare;
            System.out.println("ADDR received: " + addrShare.length + " entries");

            // Step 4: Receive opt_inv
            long[] optInvShare = (long[]) this.serverDispatcher.dboQueue.take().getPayload();
            this.serverDispatcher.optInv = optInvShare;
            System.out.println("Inverted index received: " + optInvShare.length + " entries");

            long maxPosition = 0;
            for (int k = 0; k < addrShare.length; k++) {
                long startPos = addrShare[k][0];
                long count = addrShare[k][1];
                maxPosition = Math.max(maxPosition, startPos + count);
            }

            // colSize is slightly larger than maxPosition (to account for spacing)
            // We can infer it from the pattern or calculate conservatively
            this.serverDispatcher.optCol = colSize;
            this.serverDispatcher.optRow = optInvShare.length / colSize;

            System.out.println("OptInv length: " + optInvShare.length);

            System.out.println("opt dimensions set: optRow=" + this.serverDispatcher.optRow +
                    ", optCol=" + this.serverDispatcher.optCol);

            // Step 5: Receive file batches
            // ✅ NEW: Receive files in batches and combine them

            // Calculate number of batches
            int BATCH_SIZE = 5000;
            int totalBatches = (int) Math.ceil((double) numFiles / BATCH_SIZE);

            // Initialize storage for all files
            this.serverDispatcher.files = new long[numFiles][];
            this.serverDispatcher.fileKeyword = new long[numFiles][];

            System.out.println("\nReceiving " + totalBatches + " batches of files...");

            int fileIndex = 0;
            for (int batch = 0; batch < totalBatches; batch++) {
                System.out.println("Receiving batch " + (batch + 1) + "/" + totalBatches + "...");

                // Receive file content batch
                long[][] batchContentShares = (long[][]) this.serverDispatcher.dboQueue.take().getPayload();

                // Receive keyword batch
                long[][] batchKeywordShares = (long[][]) this.serverDispatcher.dboQueue.take().getPayload();

                // Copy batch into main arrays
                for (int i = 0; i < batchContentShares.length; i++) {
                    this.serverDispatcher.files[fileIndex] = batchContentShares[i];
                    this.serverDispatcher.fileKeyword[fileIndex] = batchKeywordShares[i];
                    fileIndex++;
                }

                System.out.println("  Batch " + (batch + 1) + " received (" +
                        batchContentShares.length + " files)");
            }

            System.out.println("All files received: " + numFiles);
        }
        // Step 6: Calculate optInv hash values
        calOptInvHashValues();

        // Initialize server state
        this.serverDispatcher.keywordCount = numKeywords;
        this.serverDispatcher.fileCount = numFiles;
        this.serverDispatcher.clientCount = clientCount;
        this.serverDispatcher.initialized = true;

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BULK INITIALIZATION COMPLETE");
        System.out.println("=".repeat(80));
    }

    /**
     * Update optInv with sparse delta from DBO
     * This receives SparseDelta instead of full 245M array
     */
    public void updateOptValues() throws InterruptedException {

        System.out.println("\n=== SERVER: RECEIVING SPARSE DELTA ===");

        // Receive sparse delta from DBO
        Object received = this.serverDispatcher.dboQueue.take().getPayload();

        if (received instanceof SparseDelta) {
            SparseDelta sparseDelta = (SparseDelta) received;

            System.out.println("Received: " + sparseDelta);

            // Check for termination signal (empty delta)
            if (sparseDelta.isEmpty()) {
                System.out.println("Received termination signal (empty delta)");
                return;
            }

            // ========================================================================
            // Apply sparse delta to optInv
            // ========================================================================

            int[] positions = sparseDelta.getPositions();
            long[] values = sparseDelta.getValues();

            System.out.println("Applying " + positions.length + " updates to optInv...");

            Instant applyStart = Instant.now();

            // Apply each position update
            for (int i = 0; i < positions.length; i++) {
                int pos = positions[i];
                long delta = values[i];

                // Update optInv: add delta to existing value (modulo)
                this.serverDispatcher.optInv[pos] = Math.floorMod(this.serverDispatcher.optInv[pos] + delta, modValue);

                if (i < 5 || i == positions.length - 1) {
                    // Log first few and last update
                    System.out.println(" Position " + pos + ": " +
                            "old + " + delta + " = " + this.serverDispatcher.optInv[pos] +
                            " (mod " + modValue + ")");
                } else if (i == 5) {
                    System.out.println(" ... (" + (positions.length - 6) + " more updates) ...");
                }
            }

            Instant applyEnd = Instant.now();
            long applyTime = Duration.between(applyStart, applyEnd).toMillis();

            System.out.println("Applied in " + applyTime + " ms");
            System.out.println("=======================================\n");
        }

        else if (received instanceof ResizeOptRequest) {
            ResizeOptRequest req = (ResizeOptRequest) received;

            System.out.println("Processing RESIZE/SHIFT at index: " + req.insertIndex);

            long[] oldOptInv = this.serverDispatcher.optInv;
            int oldLen = oldOptInv.length;
            int newLen = oldLen + req.insertRowData.length; // usually + optCol

            // 1. Create new, larger array
            long[] newOptInv = new long[newLen];

            // 2. Copy Part 1: Start -> replaceIndex (exclusive)
            // Actually, usually 'replaceIndex' is where we start writing new data
            // immediately.
            // If replaceIndex > 0, we copy the data before it.
            if (req.replaceIndex > 0) {
                System.arraycopy(oldOptInv, 0, newOptInv, 0, req.replaceIndex);
            }

            // 3. Write Part 2: The REPLACEMENT Row (Row A)
            // We overwrite the old row data with the new shares (which might include new
            // files that fit)
            System.arraycopy(req.replaceRowData, 0, newOptInv, req.replaceIndex, req.replaceRowData.length);

            // 4. Write Part 3: The INSERTION Row (Row B - The Overflow)
            // This goes exactly at insertIndex (which should be replaceIndex + optCol)
            System.arraycopy(req.insertRowData, 0, newOptInv, req.insertIndex, req.insertRowData.length);

            // 5. Write Part 4: The Rest of the Array (Shifted)
            // We copy from oldOptInv starting at 'req.insertIndex' (the point after the
            // replaced row)
            // to the end. In the new array, this lands after the inserted row.
            int remainingLength = oldLen - req.insertIndex;
            if (remainingLength > 0) {
                System.arraycopy(oldOptInv, req.insertIndex,
                        newOptInv, req.insertIndex + req.insertRowData.length,
                        remainingLength);
            }

            // 6. Update Reference
            this.serverDispatcher.optInv = newOptInv;

            System.out.println("Resize Complete. New Size: " + newOptInv.length);
        }

    }

    /**
     * to perform operation between ACT list and search keyword
     * 
     * @param threadNum
     */
    public void task14(int threadNum) {
        int batchSize = (int) Math.ceil(serverDispatcher.keywordCount / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }
        BigInteger[] keywords = serverDispatcher.act[0];
        BigInteger temp;
        for (int i = start; i < end; i++) {
            temp = (keywords[i].subtract(searchKeywordShare)).mod(modValueBig);
            phase1Result[threadNum - 1].append(temp).append(",");
        }
    }

    public void getKeywordIndex() throws InterruptedException, UnsupportedEncodingException {
        stage = "1";
        // Waiting for client to send the search keyword shares
        // Performing phase1 operation on the data received from the client
        // reading the data sent by client
        Object keywordClientData = serverDispatcher.dboQueue.take().getPayload();
        searchKeywordShare = ((BigInteger[]) keywordClientData)[0];

        // System.out.println(searchKeywordShare);
        // initializing
        phase1Result = new StringBuilder[numThreads];
        for (int i = 0; i < numThreads; i++) {
            phase1Result[i] = new StringBuilder("");
        }
        // computing phase 1 operation on ACT list
        stageLabel = "4";
        createThreads(stage, stageLabel);
        // adding thread data
        for (int i = 1; i < numThreads; i++) {
            phase1Result[0].append(phase1Result[i]);
        }
        if (phase1Result[0].length() > 0 &&
                phase1Result[0].charAt(phase1Result[0].length() - 1) == ',') {
            phase1Result[0].deleteCharAt(phase1Result[0].length() - 1);
        }

        phase1Result[0].append("|").append(Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1);
        // converting to byte array
        byte[] byteList = phase1Result[0].toString().getBytes(StandardCharsets.UTF_8);
        // sending to client
        sendToClient(byteList);
    }

    // multithreaded across the column i.e. the content of addr table
    private void task21(int threadNum) {
        int batchSize = (int) Math.ceil(serverDispatcher.addr[0].length / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.addr[0].length) {
            end = serverDispatcher.addr[0].length;
        }

        for (int i = 0; i < serverDispatcher.keywordCount; i++) {
            long[] temp = serverDispatcher.addr[i];
            for (int j = start; j < end; j++) {
                phase2AddrResult[j] = (phase2AddrResult[j] +
                        (temp[j] * searchKeywordVectorShare[i]) % modValue) % modValue;
            }
        }
    }

    private boolean getAddrData() throws InterruptedException, IOException, ClassNotFoundException {
        stage = "2";

        // Waiting for client to send the data

        // performing phase 2 operations
        searchKeywordVectorShare = (long[]) serverDispatcher.dboQueue.take().getPayload();

        if (searchKeywordVectorShare[0] != 0) {
            phase2AddrResult = new long[serverDispatcher.addr[0].length + 1]; // 1 to store the label for the server
            stageLabel = "1";
            createThreads(stage, stageLabel);
            phase2AddrResult[phase2AddrResult.length
                    - 1] = (Math.floorMod(serverDispatcher.serverNumber - 2, serverCount)
                            + 1);
            // sending to client
            sendToClient(phase2AddrResult);
            return true;
        }
        return false;
    }

    /**
     * to add new files with existing keywords
     * 
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void addFilesOpt()
            throws IOException, InterruptedException, ClassNotFoundException {

        int[] dimensions = { this.serverDispatcher.optRow, this.serverDispatcher.optCol };
        sendToClient(dimensions);

        getKeywordIndex(); // get keyword index

        boolean flag = getAddrData();

        if (flag) {
            getOptValues();
            updateOptValues();

            updateAddrData();
        }
    }

    public void addFileContent() throws InterruptedException, IOException, ClassNotFoundException {
        System.out.println("\n=== SERVER " + serverDispatcher.serverNumber + " ADD FILE CONTENT ===");

        // Receive file content share from DBO
        long[] fileContentShare = (long[]) serverDispatcher.dboQueue.take().getPayload();

        System.out.println("Received file content share: " + fileContentShare.length + " elements");

        // Insert BEFORE the last row (sample file)
        int insertIndex = serverDispatcher.files.length - 1; // Before sample file

        // Expand files array by 1
        serverDispatcher.files = Arrays.copyOf(serverDispatcher.files, serverDispatcher.files.length + 1);

        // Shift sample file to end
        serverDispatcher.files[serverDispatcher.files.length - 1] = serverDispatcher.files[insertIndex];

        // Insert new file at insertIndex
        serverDispatcher.files[insertIndex] = fileContentShare;

        System.out.println("File content added at index " + insertIndex);

        // Receive keyword list share from DBO
        long[] keywordListShare = (long[]) serverDispatcher.dboQueue.take().getPayload();

        System.out.println("Received keyword list share: " + keywordListShare.length + " keywords");

        // Insert BEFORE the last row (sample file metadata)
        insertIndex = serverDispatcher.fileKeyword.length - 1;

        // Expand fileKeyword array by 1
        serverDispatcher.fileKeyword = Arrays.copyOf(serverDispatcher.fileKeyword,
                serverDispatcher.fileKeyword.length + 1);

        // Shift sample file metadata to end
        serverDispatcher.fileKeyword[serverDispatcher.fileKeyword.length
                - 1] = serverDispatcher.fileKeyword[insertIndex];

        // Insert new file's keyword list at insertIndex
        serverDispatcher.fileKeyword[insertIndex] = keywordListShare;

        System.out.println("Keyword list added at index " + insertIndex);

        // Update file count (doesn't include sample file)
        serverDispatcher.fileCount++;
        System.out.println("File count updated: " + serverDispatcher.fileCount);

        System.out.println("\n FILE CONTENT ADDED TO SERVER " + serverDispatcher.serverNumber + " ");
    }

    public void batchAddFileToKeywords() throws InterruptedException {
        System.out.println("\n=== BATCH ADD FILE TO KEYWORDS ===");

        // Receive batch data
        long[] batchData = (long[]) serverDispatcher.dboQueue.take().getPayload();

        int numKeywords = (int) batchData[0];
        int fileId = (int) batchData[batchData.length - 1];

        System.out.println("Adding file " + fileId + " to " + numKeywords + " keywords");

        // Update each keyword's inverted index
        for (int i = 0; i < numKeywords; i++) {
            int keywordIndex = (int) batchData[i + 1];

            // Get starting position and count for this keyword
            int startPos = (int) serverDispatcher.addr[keywordIndex][0];
            int count = (int) serverDispatcher.addr[keywordIndex][1];

            // Add file at position startPos + count
            int position = startPos + count;
            serverDispatcher.optInv[position] += fileId; // Add file share

            // Update ADDR count
            serverDispatcher.addr[keywordIndex][1]++;

            System.out.println("Keyword " + keywordIndex + ": added at position " + position);
        }

        System.out.println("Batch update complete");
    }

    private void task01(int threadNum) {

        int batchSize = (int) Math.ceil((serverDispatcher.keywordCount) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }

        try {
            for (int k = 0; k < serverDispatcher.act.length; k++) {
                String[] str = new String(actUpdate[k], StandardCharsets.UTF_8).split(",");
                for (int i = start; i < end; i++) {
                    serverDispatcher.act[k][i] = (serverDispatcher.act[k][i].add(new BigInteger(str[i])))
                            .mod(modValueBig);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void task02(int threadNum) {
        int batchSize = (int) Math.ceil((serverDispatcher.keywordCount) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;

        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }

        for (int k = start; k < end; k++) {
            for (int i = 0; i < 2 + 2 * Constant.getHashBlockCount(); i++) {
                serverDispatcher.addr[k][i] = Helper.mod(serverDispatcher.addr[k][i] + addrUpdate[k][i]);
            }
        }
    }

    public void deleteKeywordOpt() throws IOException, InterruptedException, ClassNotFoundException {

        if (serverDispatcher.serverNumber <= 3) {
            getKeywordIndex();
        }

        actUpdate = (byte[][]) serverDispatcher.dboQueue.take().getPayload();

        // if (!new String(actUpdate[0]).split(",")[0].equals("0")) {

        stage = "0";
        stageLabel = "1";
        createThreads(stage, stageLabel);

        // {
        //
        //
        // // update the addr list
        // if (serverNumber <= 3) {
        // boolean flag = getAddrData();
        // if (flag) {
        // removeTime.add(Instant.now());
        // comTime.add(Instant.now());
        // startServer();
        // comTime.add(Instant.now());
        // perServerTime.add(Helper.calculateSendToServerTime(comTime, waitTime, 1));
        // comTime = new ArrayList<>();
        // waitTime = new ArrayList<>();
        // removeTime.add(Instant.now());
        //
        // addrUpdate = (long[][]) objects;
        //
        // stage = "0";
        // stageLabel = "2";
        // createThreads(stage, stageLabel);
        //
        //// for (int i = 0; i < 2 + 2 * Constant.getHashBlockCount(); i++) {
        //// System.out.print(addr[0][i] + " ");
        //// }
        //// System.out.println();
        // }
        // }
        // }
        // }

    }

    public void deleteFileOpt()
            throws IOException, NoSuchAlgorithmException, InterruptedException, ClassNotFoundException {

        int[] dimensions = { this.serverDispatcher.optRow, this.serverDispatcher.optCol };
        sendToClient(dimensions);

        getKeywordIndex();

        boolean flag = getAddrData();
        if (flag) {
            getOptValues();
            stage = "2";
            newOptInv = (long[]) serverDispatcher.dboQueue.take().getPayload();
            stageLabel = "5";
            createThreads(stage, stageLabel);
            //
            // System.out.println("he");
            // for (int i=0;i<optInv.length;i++){
            // System.out.print(optInv[i]+" ");
            // }
            // System.out.println();
        }
    }

    /**
     * clean variables after an operation
     */
    public void cleanUpOpData() {
        perServerTime = new ArrayList<>();
        removeTime = new ArrayList<>();
        comTime = new ArrayList<>();
        waitTime = new ArrayList<>();
    }

    /**
     * To compare search keyword with server keyword and access value for the client
     * multithreaded across number of keywords
     * 
     * @param threadNum the number of threads to run the program on
     */
    public void task11(int threadNum) {
        int batchSize = (int) Math.ceil(serverDispatcher.keywordCount / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }
        BigInteger[] keywords = serverDispatcher.act[0];
        BigInteger[] access = serverDispatcher.act[clientId];
        BigInteger temp;
        for (int i = start; i < end; i++) {
            temp = ((keywords[i].subtract(searchKeywordShare)).mod(modValueBig).add(access[i])).mod(modValueBig);

            phase1Result[threadNum - 1].append(temp).append(",");
        }
    }

    /**
     * to update the access for the client
     * multithreaded across number of keywords
     * 
     * @param threadNum
     */
    public void task13(int threadNum) {
        int batchSize = (int) Math.ceil(serverDispatcher.keywordCount / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > serverDispatcher.keywordCount) {
            end = serverDispatcher.keywordCount;
        }
        BigInteger[] access = serverDispatcher.act[clientId];
        for (int i = start; i < end; i++) {
            serverDispatcher.act[clientId][i] = (new BigInteger(newAccess[i]).add(access[i])).mod(modValueBig);
            // System.out.print(act[clientId][i]+" ");
        }
        // System.out.println();
    }

    /**
     * @throws InterruptedException
     * @throws UnsupportedEncodingException
     */
    public void getAccessKeyword() throws InterruptedException, UnsupportedEncodingException {
        stage = "1";
        // Waiting for client to send the search keyword shares
        Object keywordClientData = serverDispatcher.dboQueue.take().getPayload();
        // reading the data sent by client
        searchKeywordShare = ((BigInteger[]) keywordClientData)[0];
        clientId = ((BigInteger[]) keywordClientData)[1].intValue() + 1; // since 1st row are keywords and 2nd row are
                                                                         // position of
        // keywords
        // initializing

        System.out.println("Number of threads" + numThreads);
        phase1Result = new StringBuilder[numThreads];
        for (int i = 0; i < numThreads; i++) {
            phase1Result[i] = new StringBuilder("");
        }
        // computing phase 1 operation on ACT list
        stageLabel = "1";
        createThreads(stage, stageLabel);
        // adding thread data
        for (int i = 1; i < numThreads; i++) {
            phase1Result[0].append(phase1Result[i]);
        }

        if (phase1Result[0].length() > 0 &&
                phase1Result[0].charAt(phase1Result[0].length() - 1) == ',') {
            phase1Result[0].deleteCharAt(phase1Result[0].length() - 1);
        }

        phase1Result[0].append("|").append(Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1);

        // phase1Result[0].append(Math.floorMod(serverNumber - 2, serverCount) + 1);
        // converting to byte array
        byte[] byteList = phase1Result[0].toString().getBytes(StandardCharsets.UTF_8);
        // sending to client
        System.out.println("Sending to client");
        sendToClient(byteList);
    }

    /**
     * to grant or revoke access of a keyword for a given client
     */
    public void revokeGrantAccess()
            throws UnsupportedEncodingException, InterruptedException {
        if (serverDispatcher.serverNumber <= 3) {
            getAccessKeyword();// get access and index of keyword
        } else {
            return;
        }
        // waiting for server to send update access result
        byte[] temp1 = (byte[]) serverDispatcher.dboQueue.take().getPayload();
        if (temp1[0] == 0)
            return;
        String objectRead = new String(temp1, StandardCharsets.UTF_8);
        clientId = Integer.parseInt(objectRead.substring(objectRead.lastIndexOf(",") + 1)) + 1;
        objectRead = objectRead.substring(0, objectRead.lastIndexOf(","));
        newAccess = objectRead.split(",");
        if (!newAccess[0].equals("0")) {
            // updating the access values
            stage = "1";
            stageLabel = "3";
            createThreads(stage, stageLabel);
        }
    }

    /**
     * to grant or revoke access of a keyword for a given client
     */
    public void revokeGrantAccessBatch()
            throws UnsupportedEncodingException, InterruptedException {

        int keywordCountToProcess = (int) serverDispatcher.dboQueue.take().getPayload();

        if (serverDispatcher.serverNumber <= 3) {
            for (int i = 0; i < keywordCountToProcess; i++)
                getAccessKeyword();// get access and index of keyword
        }

        // waiting for server to send update access result
        byte[] temp1 = (byte[]) serverDispatcher.dboQueue.take().getPayload();
        if (temp1[0] == 0)
            return;
        String objectRead = new String(temp1, StandardCharsets.UTF_8);
        clientId = Integer.parseInt(objectRead.substring(objectRead.lastIndexOf(",") + 1)) + 1;
        objectRead = objectRead.substring(0, objectRead.lastIndexOf(","));
        newAccess = objectRead.split(",");
        if (!newAccess[0].equals("0")) {
            // updating the access values
            stage = "1";
            stageLabel = "3";
            createThreads(stage, stageLabel);
        }
    }

    /**
     * Retrieves the list of all keywords from the main server's memory
     * and sends it back to the DBO client that requested it.
     */
    public void getAllKeywords() {
        System.out.println("\n=== SENDING ACT KEYWORDS ===");

        serverCount = 3;
        // Convert act[0] (keywords) to byte array with server label
        StringBuilder keywordsData = new StringBuilder();
        for (int i = 0; i < serverDispatcher.act[0].length; i++) {
            keywordsData.append(serverDispatcher.act[0][i].toString() + ":" + (serverDispatcher.addr[i][1] - 8))
                    .append(",");
        }
        if (keywordsData.length() > 0 &&
                keywordsData.charAt(keywordsData.length() - 1) == ',') {
            keywordsData.deleteCharAt(keywordsData.length() - 1);
        }

        keywordsData.append("|")
                .append(Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1);
        System.out.println((Math.floorMod(serverDispatcher.serverNumber - 2, serverCount) + 1)
                + "server label appended");

        byte[] data = keywordsData.toString().getBytes(StandardCharsets.UTF_8);

        System.out.println("Sending " + serverDispatcher.act[0].length + " keyword shares");
        sendToClient(data);
    }

    public void addNewClient() throws InterruptedException, IOException {
        System.out.println("\n=== SERVER " + serverDispatcher.serverNumber + " ADD NEW CLIENT ===");

        // Expand ACT by 1 row
        int oldRowCount = serverDispatcher.act.length;
        serverDispatcher.act = Arrays.copyOf(serverDispatcher.act, oldRowCount + 1);

        // Receive access data for existing keywords (or empty signal)
        Object data = serverDispatcher.dboQueue.take().getPayload();

        if (data instanceof byte[] && ((byte[]) data).length == 1 && ((byte[]) data)[0] == 0) {
            // No keywords yet - just initialize empty row
            System.out.println("No keywords yet - empty access row");
            serverDispatcher.act[oldRowCount] = new BigInteger[0];

        } else {
            // Parse access shares for existing keywords
            String accessData = new String((byte[]) data, StandardCharsets.UTF_8);
            String[] parts = accessData.split(",");

            System.out.println("Received access data for " + parts.length + " keywords");

            serverDispatcher.act[oldRowCount] = new BigInteger[serverDispatcher.keywordCount];

            for (int i = 0; i < parts.length && i < serverDispatcher.keywordCount; i++) {
                serverDispatcher.act[oldRowCount][i] = new BigInteger(parts[i]);
            }
        }

        serverDispatcher.clientCount++;

        System.out.println("Client added");
        System.out.println(" ACT rows: " + serverDispatcher.act.length);
        System.out.println("Client count: " + serverDispatcher.clientCount);

        sendToClient("SUCCESS".getBytes(StandardCharsets.UTF_8));
    }

    public void getAllFiles() {
        try {
            FilesDataStructureShares filesShare = new FilesDataStructureShares(
                    Math.floorMod(serverDispatcher.serverNumber - 2, serverCount),
                    serverDispatcher.files);

            sendToClient(filesShare);
        } catch (IndexOutOfBoundsException e) {
            log.warning("DBO client requested a non-existent file ID");
            // Send back an empty or error response
            sendToClient(new long[0]);
        }
    }

    public void getFileShare() {
        serverCount = 3;
        try {
            int fileId = (int) this.serverDispatcher.dboQueue.take().getPayload() - 1;
            FileShare fs = new FileShare(Math.floorMod(serverDispatcher.serverNumber - 2, serverCount),
                    serverDispatcher.files[fileId]);
            System.out.println("Server Share ID: " + fs.getServerNumber());
            sendToClient(fs);
        } catch (IndexOutOfBoundsException | InterruptedException e) {
            log.warning("DBO client requested a non-existent file ID");
            // Send back an empty or error response
            sendToClient(new long[0]);
        }
    }

    public void getFileCount() {
        System.out.println("Files count inside DBO is " + serverDispatcher.fileCount);
        sendToClient(serverDispatcher.fileCount);
    }

    public void getClientCount() {
        System.out.println("Files count inside DBO is " + serverDispatcher.files.length);
        sendToClient(serverDispatcher.clientCount);
    }

    public void getKeywordCount() {
        System.out.println("Keywords count inside DBO is " + serverDispatcher.act[0].length);
        if (serverDispatcher.act[0] == null)
            sendToClient(0);
        else
            sendToClient(serverDispatcher.act[0].length);
    }

    public void getStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("keywords", serverDispatcher.act[0].length);
        stats.put("clients", serverDispatcher.clientCount);
        stats.put("documents", serverDispatcher.fileCount);
        System.out.println("Sending stats: " + stats);
        sendToClient(stats);
    }

    public void resetServer() {
        System.out.println("\n=== RESETTING SERVER " + serverDispatcher.serverNumber + " ===");
        serverDispatcher.loadDataStructures();
        serverDispatcher.clientCount = 0;
        serverDispatcher.keywordCount = 0;
        serverDispatcher.optRow = 0;
        serverDispatcher.optCol = 0;
        serverDispatcher.optInvHashValues = null;
        System.out.println("Server reset");
        sendToClient("SUCCESS");
    }
}
