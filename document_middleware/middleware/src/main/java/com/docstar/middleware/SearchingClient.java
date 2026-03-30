package com.docstar.middleware;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

import com.docstar.DocStarApplication;
import com.docstar.model.dto.ClientRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SearchingClient extends Thread {

    @Autowired
    @Qualifier("commonProperties")
    Properties commonProperties = new Properties();

    private final Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    // the mod value
    private final long modValue = Constant.getModParameter();
    private final int hashBlockCount = Constant.getHashBlockCount();
    private final int hashBlockSize = Constant.getHashBlockSize();
    // the mod value for BigInteger
    private final BigInteger modValueBig = Constant.getModBigParameter();
    private String searchKeyword;
    private String clientId;

    private int serverCount;
    private long[][] keywordVectorShares;
    private int keywordCount;
    private long[] keywordVector;
    // to store the number of files
    private int fileCount;
    // to store the index of the keyword in act table
    private int phase1Result;
    private ArrayList<Long> phase2Result;
    private long[] hash_list;
    private long[][] phase3Result;
    private boolean serverVerification;
    private boolean clientVerification;
    private long[][] fileVectors;
    private long[][][] fileVectorsShares;
    private long[][] hash;
    private String type;
    private int numThreads;
    private String stage;
    private String stageLabel;
    private boolean flagCVP1;
    private BigInteger[] servers_1_2_3;
    private BigInteger[] servers_1_2_4;
    private BigInteger[] servers_2_3_4;
    private BigInteger[] servers_1_3_4;
    private ArrayList<Instant> comTime = new ArrayList<>();
    private ArrayList<Instant> waitTime = new ArrayList<>();
    private int fileLength;
    private long[][] objectsReceivedLong1D;
    // to stores server shares as long values for 2D array
    private long[][][] objectsReceivedLong2D;
    // to stores server shares as long values for 1D array
    private String[][] objectsReceivedString1D;
    // to stores server shares as long values for 2D array
    private String[][][] serverSharesAsString_2D;
    private String[][] content;
    private String[] finalContent;
    private ArrayList<Instant> removeTime;
    private ArrayList<Double> perServerTime;
    private String[] serverIP;
    private int[] serverPort;
    private ServerSocket ss;
    private long[] hashServer;
    // to store stemmer object
    private SnowballStemmer stemmer;
    // to store the numeric version of text
    private StringBuilder numericText;
    // to store the updates text
    private long[] optInvRowVec;
    private long[] optInvColVec;
    private long[][] optInvRowVecShare;
    private long[][] optInvColVecShare;
    private int optRow;
    private int optCol;
    private int startingPos;
    private int countItems;
    private long[][][] optIndexShare;
    private long[][] serverVerificationPhase3;
    private long[][][] verificationServer2DPhase3;
    private int checkpoint1;
    private boolean ret = false;

    private int fetchCount = 0;

    private String sessionId;

    Map<Integer, ServerConnection> serverConnections = new HashMap<>();

    public void setKeyword(String keyword) {
        searchKeyword = keyword;
    }

    public void setClientId(String user) {
        clientId = user;
    }

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
        BigInteger result = (new BigInteger(digest)).mod(modValueBig);
        return result.toString();
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Convert keyword to number
     *
     * @param data the keyword
     * @return the numeric format of keyword
     */
    public void keywordToNumber(String data) {
        numericText = new StringBuilder("");
        for (int i = 0; i < data.length(); i++) {
            numericText.append((int) data.charAt(i) - 86);
        }
    }

    /**
     * To convert numeric string to string values
     *
     * @param numericString the numeric format of string
     * @return the cleartext string
     */
    public String decrypt_numeric_string(String numericString) {
        StringBuilder text = new StringBuilder();
        int substring;
        if (numericString.length() % 2 == 1)
            numericString = numericString.substring(0, numericString.length() - 1);

        for (int i = 0; i < numericString.length(); i = i + 2) {
            substring = Integer.parseInt(numericString.substring(i, (i + 2)));

            if (substring >= 10 && substring <= 99) {
                if (substring == 10) {
                    text.append((char) (96));
                } else if (substring <= 14) {
                    text.append((char) (substring + 112));
                } else if (substring >= 65 && substring <= 90) {
                    text.append((char) (substring + 32));
                } else {
                    text.append((char) (substring));
                }
            }

        }

        return text.toString();
    }

    /**
     * To perform cleanup tasks in course of program execution
     */
    public void cleanUpPhaseData(int phase) {
        switch (phase) {
            case 1 -> {
                servers_1_2_3 = new BigInteger[numThreads];
                servers_1_2_4 = new BigInteger[numThreads];
                servers_1_3_4 = new BigInteger[numThreads];
                servers_2_3_4 = new BigInteger[numThreads];
                perServerTime = new ArrayList<>();
                removeTime = new ArrayList<>();
            }
            case 2 -> {
                objectsReceivedLong1D = null;
                keywordVectorShares = new long[serverCount][keywordCount];
                hashServer = new long[hashBlockCount];
                optInvRowVec = new long[optRow];
                optInvColVec = new long[optCol];
                optInvRowVecShare = new long[serverCount][optRow];
                optInvColVecShare = new long[serverCount][optCol];
                optIndexShare = new long[serverCount][2][];
                hash_list = new long[hashBlockCount];
                perServerTime = new ArrayList<>();
                removeTime = new ArrayList<>();
            }
            case 3 -> {
                // GC
                objectsReceivedLong1D = null;
                verificationServer2DPhase3 = null;
                phase3Result = null;
                hash = null;
                content = null;
                perServerTime = new ArrayList<>();
                removeTime = new ArrayList<>();
                fileVectors = new long[fetchCount][fileCount + 1]; // one for sample file
                fileVectorsShares = new long[serverCount][fetchCount + 1][fileCount + 1];
                hash = new long[fetchCount][hashBlockCount];
                content = new String[fetchCount][numThreads];
                serverVerificationPhase3 = new long[fetchCount][checkpoint1];
                finalContent = new String[fetchCount];
            }
        }
    }

    /**
     * To interpolate share values to retrieve secret
     * 
     * @param share the shares
     * @return the cleartext/interpolated value
     */
    public BigInteger lagrangeInterpolation(BigInteger[] share) {
        return switch (share.length) {
            case 2 -> ((BigInteger.valueOf(2).multiply(share[0])).mod(modValueBig)
                    .subtract(share[1])).mod(modValueBig);

            case 3 -> {
                // Correct formula: (3*s0 - 3*s1 + s2) / 2
                BigInteger numerator = (BigInteger.valueOf(3).multiply(share[0]))
                        .subtract(BigInteger.valueOf(3).multiply(share[1]))
                        .add(share[2])
                        .mod(modValueBig);

                // Modular division by 2 = multiply by modular inverse of 2
                BigInteger two = BigInteger.valueOf(2);
                BigInteger twoInverse = two.modInverse(modValueBig);

                yield numerator.multiply(twoInverse).mod(modValueBig);
            }

            default -> throw new IllegalStateException("Unexpected value: " + share.length);
        };
    }

    /**
     * To interpolate share values to retrieve secret
     *
     * @param share the shares
     * @return the cleartext/interpolated value
     */
    public BigInteger lagrangeInterpolation(BigInteger[] share, long x0, long x1, long x2) {
        BigInteger result;
        BigDecimal part_a = new BigDecimal((BigInteger.valueOf(x1 * x2).multiply(share[0])))
                .divide(new BigDecimal(BigInteger.valueOf(((x0 - x1) * (x0 - x2)))), 10, RoundingMode.DOWN);
        BigDecimal part_b = new BigDecimal((BigInteger.valueOf(x0 * x2).multiply(share[1])))
                .divide(new BigDecimal(BigInteger.valueOf(((x1 - x0) * (x1 - x2)))), 10, RoundingMode.DOWN);
        BigDecimal part_c = new BigDecimal((BigInteger.valueOf(x0 * x1).multiply(share[2])))
                .divide(new BigDecimal(BigInteger.valueOf(((x2 - x0) * (x2 - x1)))), 10, RoundingMode.DOWN);

        BigDecimal a = (part_a.add(part_b).add(part_c)).divide(BigDecimal.valueOf(1), 0, RoundingMode.CEILING);
        result = (new BigInteger(String.valueOf(a))).mod(modValueBig);
        return result;
    }

    /**
     * To interpolate share values to retrieve secret
     *
     * @param share the shares
     * @return the cleartext/interpolated value
     */
    public long lagrangeInterpolation(long[] share) {
        return switch (share.length) {
            case 2 -> Math.floorMod((((2 * share[0]) % modValue) - share[1]), modValue);
            case 3 -> (Math.floorMod((((3 * share[0]) % modValue) -
                    ((3 * share[1]) % modValue)), modValue) + share[2]) % modValue;
            default -> throw new IllegalStateException("Unexpected value: " +
                    share.length);
        };
    }

    /**
     * start client to receive data from server
     *
     * @param serverCount the number of server to receive from
     */
    public List<Object> startClient(int serverCount) {
        List<Object> objects = Collections.synchronizedList(new ArrayList<>());
        try {
            int server = 1;
            System.out.println("Client Listening........");
            while (objects.size() != serverCount) {
                // Reading data from the server
                waitTime.add(Instant.now());
                ServerConnection serverConnection = serverConnections.get(server);
                Object obj = serverConnection.receive();
                objects.add(obj);
                server++;
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, ex.getMessage());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return objects;
    }

    /**
     * To create a socket and send data to the server
     *
     * @param data the data to be sent
     * @param IP   the IP of the target server
     * @param port the port of the target server
     * @throws IOException
     */
    public void sendToServer(Object data, int targetServer) throws IOException {
        try {
            waitTime.add(Instant.now());
            ClientRequest request = new ClientRequest(sessionId, data);
            ServerConnection serverConnection = serverConnections.get(targetServer);
            serverConnection.send(request);

            if ("RESTART".equals(data)) {
                Object ack = serverConnection.receive();
                if ("RESTARTED".equals(ack)) {
                    log.log(Level.INFO, "Server " + targetServer + " acknowledged RESTART.");
                } else {
                    throw new IOException("Unexpected response from server: " + ack);
                }
            }
        } catch (IOException | ClassNotFoundException ex) {
            log.log(Level.SEVERE, ex.getMessage());
        }
    }

    /**
     * To read values from objects
     *
     * @param type the type of object as 1D or 2D
     */
    public void readObjectsAsLong(String type, List<Object> objects) {
        if (type.equals("1D")) {
            for (int i = 0; i < objects.size(); i++) {
                int temp = (int) (((long[]) objects.get(i))[((long[]) objects.get(i)).length - 1]);
                objectsReceivedLong1D[temp - 1] = ((long[]) objects.get(i));
            }
        } else if (type.equals("2D")) {
            for (int i = 0; i < objects.size(); i++) {
                int temp = (int) (((long[][]) objects.get(i))[((long[][]) objects.get(i)).length - 1][0]);
                objectsReceivedLong2D[temp - 1] = ((long[][]) objects.get(i));
            }
        }
    }

    /**
     * To read values from objects
     *
     * @param type the type of object as 1D or 2D
     */
    public void readObjectsAsString(String type, List<Object> objects) {
        if (type.equals("1D")) {
            for (int i = 0; i < objects.size(); i++) {
                String objectRead = new String((byte[]) objects.get(i), StandardCharsets.UTF_8);
                int temp = objectRead.charAt(objectRead.length() - 1) - '0';
                objectsReceivedString1D[temp - 1] = objectRead.split(",");
            }

        } else if (type.equals("2D")) {
            for (int i = 0; i < objects.size(); i++) {
                byte[][] data = (byte[][]) objects.get(i);
                int temp = new String(data[data.length - 1], StandardCharsets.UTF_8).charAt(0) - '0';
                serverSharesAsString_2D[temp - 1] = new String[phase2Result.size()][];
                for (int j = 0; j < data.length - 1; j++) {
                    serverSharesAsString_2D[temp - 1][j] = new String(data[j], StandardCharsets.UTF_8).split(",");
                }
            }
        }

    }

    /**
     * Create share for secret value as string
     *
     * @param secret      the secret value as string
     * @param serverCount the number of servers for which shares are to be created
     * @return the share of the secret value as string is returned for given number
     *         of server as servercount
     */
    public BigInteger[] shamirSecretShares(StringBuilder secret, int serverCount) {

        BigInteger secretBig = new BigInteger(String.valueOf(secret));
        Random rand = new Random();
        BigInteger[] share = new BigInteger[serverCount];

        BigInteger slope = BigInteger
                .valueOf(rand.nextLong(Constant.getMaxRandomBound() - Constant.getMinRandomBound()) +
                        Constant.getMinRandomBound());

        // check if the secret size is enough for long or BigInteger calculation
        for (int i = 0; i < serverCount; i++) {
            share[i] = ((BigInteger.valueOf(i + 1).multiply(slope)).add(secretBig)).mod(modValueBig);
        }
        return share;
    }

    /**
     * Create share for secret value as string
     *
     * @param secret      the secret value as string
     * @param serverCount the number of servers for which shares are to be created
     * @return the share of the secret value as string is returned for given number
     *         of server as servercount
     */
    public long[] shamirSecretShares(long secret, int serverCount) {
        Random rand = new Random();
        long[] share = new long[serverCount];
        // choosing the slope value for line
        long slope = rand.nextInt(Constant.getMaxRandomBound() - Constant.getMinRandomBound()) +
                Constant.getMinRandomBound();
        // check if the secret size is enough for long or BigInteger calculation
        for (int i = 0; i < serverCount; i++) {
            share[i] = (((i + 1) * slope) + secret) % modValue;
        }
        return share;
    }

    /**
     * Process input keyword and create share for secret value
     *
     * @param searchKeyword the secret value
     * @return the share of the secret value is returned for given number of server
     *         as servercount
     */
    public BigInteger[] createShares(String searchKeyword) {

        // changing all case to lowercase
        searchKeyword = searchKeyword.toLowerCase();

        // perform stemming of the token
        stemmer.setCurrent(searchKeyword);
        stemmer.stem();
        searchKeyword = stemmer.getCurrent();

        // converting keyword as string to number
        keywordToNumber(searchKeyword);

        // padding token to avoid leakage due to keyword size
        if (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
            while (numericText.length() < 2 * Constant.getMaxKeywordLength())
                numericText.append(Constant.getPadding());
        }

        // creating the shares

        return shamirSecretShares(numericText, serverCount);
    }

    /**
     * create job to be run by each thread
     */
    public class ThreadTask implements Runnable {
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
            if (stage.equals("1")) {
                if (stageLabel.equals("1"))
                    task11(threadNum);
                else if (stageLabel.equals("2")) {
                    task12(threadNum);
                }
            } else if (stage.equals("2")) {
                if (stageLabel.equals("1")) {
                    task21(threadNum);
                } else if (stageLabel.equals("2")) {
                    task22(threadNum);
                }

            } else if (stage.equals("3")) {
                if (stageLabel.equals("1")) {
                    task31(threadNum);
                } else if (stageLabel.equals("2")) {
                    task32(threadNum);
                } else if (stageLabel.equals("3")) {
                    task33(threadNum);
                }

            }

        }
    }

    /**
     * create thread for multithreaded execution
     *
     * @param stage
     * @param stageLabel
     */
    public void createThreads(String stage, String stageLabel) {
        List<Thread> threadList = new ArrayList<>();
        // Create threads and add them to thread-list
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
     * To create shares of the file vectors
     * multithreaded across number of files
     * 
     * @param threadNum the thread number
     */
    public void task31(int threadNum) {
        long[] shares;
        int batchSize = (int) Math.ceil(fileCount / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > fileCount) {
            end = fileCount;
        }

        for (int m = 0; m < fileVectors.length; m++) {
            for (int i = start; i < end; i++) {
                shares = shamirSecretShares(fileVectors[m][i], serverCount);
                for (int j = 0; j < serverCount; j++)
                    fileVectorsShares[j][m][i] = shares[j];
            }
        }
    }

    /**
     * to interpolate the content of the file
     * multithreaded across the maximum length of file
     * 
     * @param threadNum
     */
    public void task32(int threadNum) {
        long[] shares = new long[serverCount];
        int batchSize = (int) Math.ceil(fileLength / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > fileLength) {
            end = fileLength;
        }

        for (int i = 0; i < phase3Result.length; i++) {
            StringBuilder str = new StringBuilder();
            for (int j = start; j < end; j++) {
                for (int l = 0; l < serverCount; l++) {
                    shares[l] = objectsReceivedLong2D[l][i][j];
                }
                phase3Result[i][j] = lagrangeInterpolation(shares);
                if (j < hashBlockCount) {
                    hash[i][j] = phase3Result[i][j];
                } else {
                    str.append(phase3Result[i][j]);
                }
            }
            content[i][threadNum - 1] = String.valueOf(str);
        }
    }

    /**
     * interpolate the keyword vector containing the list of keywords a file
     * contains
     * 
     * @param threadNum
     */
    public void task33(int threadNum) {
        long[] shares = new long[serverCount];
        int batchSize = (int) Math.ceil((checkpoint1) / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > (checkpoint1)) {
            end = checkpoint1;
        }

        for (int i = 0; i < phase2Result.size(); i++) {
            for (int j = start; j < end; j++) {
                for (int l = 0; l < serverCount; l++) {
                    shares[l] = objectsReceivedLong2D[l][i][j];
                }
                serverVerificationPhase3[i][j] = lagrangeInterpolation(shares);
                shares = shamirSecretShares(serverVerificationPhase3[i][j], serverCount);
                for (int l = 0; l < serverCount; l++) {
                    verificationServer2DPhase3[l][i][j] = shares[l];
                }
            }
        }
    }

    /**
     * create shares of the array
     * 
     * @param data        the data
     * @param serverCount the number of servers
     * @return the shares af the data
     */
    public long[][][] createShares(long[][] data, int serverCount) {
        long[][][] result = new long[serverCount][data.length][data[0].length];
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[0].length; j++) {
                long[] shares = shamirSecretShares(data[i][j], serverCount);
                for (int l = 0; l < serverCount; l++) {
                    result[l][i][j] = shares[l];
                }
            }
        }
        return result;
    }

    /**
     * convert keywords list to size of ACT keywords
     * 
     * @param data the list of keyword indices
     * @return elongated list of size equal to ACT keywords
     */
    public long[][] fileKeyToActKey(long[][] data) {
        long[][] result = new long[data.length][keywordCount];
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[0].length; j++) { // first position if fileid
                if (data[i][j] != 0) {
                    result[i][(int) data[i][j] - 1] = 1;
                }
            }
        }
        return result;
    }

    /**
     * to fetch the file given file id
     * 
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public String phase3() throws IOException, NoSuchAlgorithmException {
        stage = "3";
        serverCount = 3;

        // preparing fileVectors vector for sending to server

        for (int k = 0; k < fetchCount; k++) {
            long file = phase2Result.get(k);
            System.out.println("Request file: " + file);
            if (file == 0) {
                file = fileCount + 1;
            }
            fileVectors[k][(int) (file - 1)] = 1;
            // fileVectors[k][(int) (file+1) -1] = 1; // Test for client correctness for
            // server: test 1
            // fileVectors[k][(int) (file + 1) - 1] = 2; // Test for client correctness
            // for
            // server: test 3, 4, 5
        }
        // create shares of the fileVectors
        stageLabel = "1";
        createThreads(stage, stageLabel);
        // sending shares to the server
        removeTime.add(Instant.now());
        comTime.add(Instant.now());
        long[][] data2D;
        int org = startingPos % optCol;
        for (int i = 0; i < serverCount; i++) {
            fileVectorsShares[Math.floorMod(i - 1, serverCount)][phase2Result.size()][0] = (i + 1);
            fileVectorsShares[Math.floorMod(i - 1, serverCount)][phase2Result.size()][1] = (org
                    + Math.floorMod(i - 1, serverCount) + 1);
            data2D = fileVectorsShares[Math.floorMod(i - 1, serverCount)];
            sendToServer(data2D, (i + 1));
        }
        comTime.add(Instant.now());
        perServerTime.add(Helper.calculateSendToServerTime(comTime, waitTime, serverCount));
        comTime = new ArrayList<>();
        waitTime = new ArrayList<>();
        removeTime.add(Instant.now());

        if (serverVerification && clientVerification) {

            List<Object> objects = startClient(serverCount);

            type = "2D";
            objectsReceivedLong2D = new long[serverCount][][];
            readObjectsAsLong(type, objects);

            boolean flag = true;
            for (int j = 0; j < serverCount; j++) {
                if (objectsReceivedLong2D[j][0][0] == 0) {
                    flag = false;
                    break;
                }
            }
            if (!flag) {
                log.info("Client has prepared an incorrect file vector");
                return "ERROR:Client has prepared an incorrect file vector";
            }
        } else {
            System.out.println("Inside only server verification");

            List<Object> objects = startClient(serverCount);

            type = "2D";
            objectsReceivedLong2D = new long[serverCount][][];
            readObjectsAsLong(type, objects);

        }
        serverVerificationPhase3 = new long[phase2Result.size()][checkpoint1];
        verificationServer2DPhase3 = new long[serverCount][phase2Result.size()][checkpoint1];
        stageLabel = "3";
        createThreads(stage, stageLabel);

        // create array out of file keyword and then create its shares
        long[][] fileKeys = fileKeyToActKey(serverVerificationPhase3);
        verificationServer2DPhase3 = createShares(fileKeys, serverCount);
        // send to server the shares
        removeTime.add(Instant.now());
        comTime.add(Instant.now());
        for (int i = 0; i < serverCount; i++) {
            data2D = verificationServer2DPhase3[Math.floorMod(i - 1, serverCount)];
            System.out.println("Data2D size: " + data2D[0].length + "x" + data2D.length);
            sendToServer(data2D, (i + 1));
        }
        comTime.add(Instant.now());
        perServerTime.add(Helper.calculateSendToServerTime(comTime, waitTime, serverCount));
        comTime = new ArrayList<>();
        waitTime = new ArrayList<>();
        removeTime.add(Instant.now());

        List<Object> objects = startClient(serverCount);

        type = "2D";
        objectsReceivedLong2D = new long[serverCount][][];
        readObjectsAsLong(type, objects);

        boolean flag = true;
        for (int j = 0; j < serverCount; j++) {
            if (objectsReceivedLong2D[j][0][0] == 0) {
                flag = false;
                break;
            }
        }
        if (!flag) {
            log.info(
                    "Client has prepared an incorrect file vector Or has no access on all keywords of requested file.");
            return "ERROR:Client has prepared an incorrect file vector Or has no access on all keywords of requested file.";
        }
        // to fetch the file content
        fileLength = objectsReceivedLong2D[0][0].length;
        phase3Result = new long[phase2Result.size()][fileLength];
        stageLabel = "2";
        createThreads(stage, stageLabel);
        for (int i = 0; i < phase3Result.length; i++) {
            finalContent[i] = "";
            for (int p = 0; p < numThreads; p++) {
                finalContent[i] = finalContent[i] + content[i][p];
            }
            finalContent[i] = decrypt_numeric_string(finalContent[i]);
        }
        if (clientVerification) {
            String hashC;
            long[] hashClient;
            int h;
            for (int i = 0; i < phase3Result.length; i++) {
                hashC = hashDigest(finalContent[i].trim());
                h = 0;
                hashClient = new long[hashBlockCount];
                for (int j = 0; j < hashC.length(); j = j + hashBlockSize) {
                    int end = j + hashBlockSize;
                    if (end > hashC.length())
                        end = hashC.length();
                    hashClient[h] = Long.parseLong(hashC.substring(j, end));
                    h++;
                }
                for (int m = 0; m < hashBlockCount; m++) {
                    // hash[i][m] = 90; // Test for server correctness for client
                    if (!(hash[i][m] == hashClient[m])) {
                        log.info("The files content provided by the servers is incorrect.");
                        flag = false;
                    }
                }
            }
            if (!flag) {
                log.info("The files content results by the servers is incorrect");
                return "ERROR:The files content results by the servers is incorrect";
            }
        }
        // System.out.println("The file/s with keyword " + searchKeyword + " is/are
        // stored in results folder.");
        // System.out.println(phase3Result.length);
        // for (int i = 0; i < phase3Result.length; i++) {
        // Helper.writeToFile(String.valueOf(phase2Result.get(i)), finalContent[i],
        // stage);
        // }
        return finalContent[0];
    }

    /**
     * To create shares of keyword vector formed in phase 2 based on keyword index
     * fetched from phase 1
     * multithreaded across number of keywords
     *
     * @param threadNum the number of threads
     */
    public void task21(int threadNum) {
        int batchSize = (int) Math.ceil(keywordCount / (double) numThreads);
        long[] shares;
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > keywordCount) {
            end = keywordCount;
        }

        for (int i = start; i < end; i++) {
            shares = shamirSecretShares(keywordVector[i], serverCount);
            for (int j = 0; j < serverCount; j++)
                keywordVectorShares[j][i] = shares[j];
        }
    }

    /**
     * to interpolate ADDR data returned from server
     */
    public void readAddrTable() {
        System.out.println("ADDR result");
        long interpolatedValue;
        long[] shares = new long[serverCount];
        for (int i = 0; i < objectsReceivedLong1D[0].length - 1; i++) {
            for (int j = 0; j < serverCount; j++) {
                shares[j] = objectsReceivedLong1D[j][i];
            }
            interpolatedValue = lagrangeInterpolation(shares);
            System.out.print(interpolatedValue + " ");
            if (i == 0) {
                startingPos = (int) interpolatedValue;
            } else if (i == 1) {
                countItems = (int) interpolatedValue;
            } else {
                hashServer[i - 2] = interpolatedValue;
            }
        }
        System.out.println();
    }

    // multithreaded across number of opt inverted index row and col

    /**
     * create shares of row and column vector of opt inverted index
     * multithreaded across number of columns
     *
     * @param threadNum thread number
     */
    public void task22(int threadNum) {

        int batchSize = (int) Math.ceil(optCol / (double) numThreads);
        long[] shares;
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > optCol) {
            end = optCol;
        }
        for (int i = start; i < end; i++) {
            shares = shamirSecretShares(optInvColVec[i], serverCount);
            for (int j = 0; j < serverCount; j++)
                optInvColVecShare[j][i] = shares[j];
        }

        batchSize = (int) Math.ceil(optRow / (double) numThreads);
        start = (threadNum - 1) * batchSize;
        end = start + batchSize;
        if (end > optRow) {
            end = optRow;
        }
        for (int i = start; i < end; i++) {
            shares = shamirSecretShares(optInvRowVec[i], serverCount);
            for (int j = 0; j < serverCount; j++)
                optInvRowVecShare[j][i] = shares[j];
        }
    }

    /**
     * to interpolate the file ids received from the server
     */
    public void phase2ResultInterpolation() {

        long interpolatedValue;
        long[] shares = new long[serverCount];

        for (int i = 0; i < countItems; i++) {
            // Collect shares from all 3 servers
            for (int j = 0; j < serverCount; j++) {
                shares[j] = objectsReceivedLong1D[j][(startingPos + i) % optCol];
            }

            interpolatedValue = lagrangeInterpolation(shares);

            if (i < hashBlockCount) {
                hash_list[i] = interpolatedValue;
            } else {
                if (interpolatedValue > 0)
                    phase2Result.add(interpolatedValue);
            }
        }

        System.out.println("\nTotal files found: " + phase2Result.size());
    }

    /**
     * To perform phase 2 operation of fetching the file ids for the search keyword
     * from inverted index and addr
     * 
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public Object phase2() throws IOException, NoSuchAlgorithmException {
        phase2Result = new ArrayList<>();
        stage = "2";
        serverCount = 3;
        {
            // retrieve addr data
            keywordVector = new long[keywordCount];
            keywordVector[phase1Result] = 1;
            // System.out.println("phase1Result:" + phase1Result);
            // keywordVector[phase1Result + 1] = 1; // Test 1,2
            // keywordVector[phase1Result] = 3; // Test 3,4,5
            stageLabel = "1";
            createThreads(stage, stageLabel);

            long[] data1D; // sending share to the server
            for (int i = 0; i < serverCount; i++) {
                data1D = keywordVectorShares[Math.floorMod(i - 1, 3)];
                sendToServer(data1D, (i + 1));
            }

            // server either sends addr data or server verification for keyword vector as
            // false if verification fails
            if (serverVerification && clientVerification) {

                List<Object> objects = startClient(serverCount);

                type = "1D";
                objectsReceivedLong1D = new long[serverCount][];
                readObjectsAsLong(type, objects);

                boolean flag = true;
                for (int j = 0; j < serverCount; j++) {
                    if (objectsReceivedLong1D[j][0] == 0) {
                        flag = false;
                        break;
                    }
                }
                if (!flag) {
                    log.info("Client has prepared an incorrect keyword vector.");
                    ret = true;
                    return "Client has prepared an incorrect keyword vector.";
                }
            } else {

                List<Object> objects = startClient(serverCount);

                type = "1D";
                objectsReceivedLong1D = new long[serverCount][]; // receive addr data
                readObjectsAsLong(type, objects);
            }
            // interpolate and get position in opt_inv, number of files for keyword and hash
            // values from addr table
            readAddrTable();
            // verifying if the addr data sent by server about the position and count items
            // is correct
            if (clientVerification) {
                String hash = hashDigest(hashDigest(String.valueOf(startingPos)) + countItems);
                long[] hashClient = new long[hashBlockCount];
                // breaking hash digest into hash blocks to compare against what is received
                // from server
                int j = 0;
                for (int i = 0; i < hash.length(); i = i + hashBlockSize) {
                    int end = i + hashBlockSize;
                    if (end > hash.length())
                        end = hash.length();
                    hashClient[j] = Long.parseLong(hash.substring(i, end));
                    j++;
                }
                // compare hash blocks with each other
                for (int i = 0; i < hashBlockCount; i++) {
                    // indexHashList[i]=90; // test for server
                    if (!(hashServer[i] == hashClient[i])) {
                        log.info("The addr content provided by the servers is incorrect.");
                        removeTime.add(Instant.now());
                        comTime.add(Instant.now());
                        for (int p = 0; p < serverCount; p++) {
                            sendToServer(new long[][] { { 0 } }, (p + 1));
                        }
                        comTime.add(Instant.now());
                        perServerTime.add(Helper.calculateSendToServerTime(comTime, waitTime, serverCount));
                        comTime = new ArrayList<>();
                        waitTime = new ArrayList<>();
                        removeTime.add(Instant.now());
                        ret = true;
                        return "The addr content provided by the servers is incorrect.";
                    }
                }
            }
        }

        int[][] dimensionsFromServers = new int[3][];
        List<Object> objects = startClient(3);
        for (int i = 0; i < 3; i++) {
            dimensionsFromServers[i] = (int[]) objects.get(i);
            System.out.println("Server " + (i + 1) + " dimensions: optRow=" +
                    dimensionsFromServers[i][0] + ", optCol=" + dimensionsFromServers[i][1]);
        }

        // ✅ Use dimensions from server (they should all be the same)
        this.optRow = dimensionsFromServers[0][0];
        this.optCol = dimensionsFromServers[0][1];

        System.out.println("✓ Using opt dimensions: optRow=" + this.optRow + ", optCol=" + this.optCol);

        // Verify all servers sent same dimensions
        for (int i = 1; i < 3; i++) {
            if (dimensionsFromServers[i][0] != this.optRow ||
                    dimensionsFromServers[i][1] != this.optCol) {
                System.err.println("⚠️ Warning: Server " + (i + 1) + " has different dimensions!");
            }
        }

        optInvRowVec = new long[optRow];
        optInvColVec = new long[optCol];
        optInvRowVecShare = new long[serverCount][optRow];
        optInvColVecShare = new long[serverCount][optCol];

        // process the data received from server to get the file ids
        // create vector to extract file_ids from opt_inv s
        optInvRowVec[startingPos / optCol] = 1;
        int startIndex = startingPos % optCol;
        int endIndex = ((startingPos + countItems) - 1) % optCol;
        for (int i = 0; i < optCol; i++) { // vector for extracting only particular columns
            if (i < startIndex || i > endIndex)
                optInvColVec[i] = 1;
            // optInvColVec[i] = 1; // test client correctness for server
        }
        // create shares of row and column vector
        stageLabel = "2";
        createThreads(stage, stageLabel);
        // sending share to the server

        long[][] data2D;
        for (int i = 0; i < serverCount; i++) {
            optIndexShare[i][0] = optInvRowVecShare[i];
            optIndexShare[i][1] = optInvColVecShare[i];
            data2D = optIndexShare[i];
            sendToServer(data2D, (i + 1));
        }

        // check if hash digest of cell in column vector match the addr hash digest
        if (serverVerification && clientVerification) {

            objects = startClient(serverCount);

            type = "1D";
            objectsReceivedLong1D = new long[serverCount][];
            readObjectsAsLong(type, objects);

            boolean flag = true;
            for (int j = 0; j < serverCount; j++) {
                if (objectsReceivedLong1D[j][0] == 0) {
                    flag = false;
                    break;
                }
            }
            if (!flag) {
                log.info("Client has prepared an incorrect opt inv row/col vector.");
                ret = true;
                return "Client has prepared an incorrect opt inv row/col vector.";
            }
        } else {

            objects = startClient(serverCount);

            type = "1D"; // receive the file ids
            objectsReceivedLong1D = new long[serverCount][];
            readObjectsAsLong(type, objects);
        }
        // interpolate the file ids received
        phase2ResultInterpolation();
        if (clientVerification) {
            String hash = hashDigest(searchKeyword);
            long[] hashClient = new long[hashBlockCount];
            // getting hash digest for file ids received
            for (long fileId : phase2Result) {
                hash = hashDigest(hash + fileId);
            }
            // breaking hash digest into hash blocks to compare against what is received
            // from server
            int j = 0;
            String temp = hash;
            for (int i = 0; i < temp.length(); i = i + hashBlockSize) {
                int end = i + hashBlockSize;
                if (end > temp.length())
                    end = temp.length();
                hashClient[j] = Long.parseLong(temp.substring(i, end));
                j++;
            }
            // formatting hash blocks to compare against each other
            for (int i = 0; i < hashBlockCount; i++) {
                // hash_list[i]=90; // test server
                if (!(hash_list[i] == hashClient[i])) {
                    log.info("The files id/s provided by the servers is incorrect.");

                    for (int p = 0; p < serverCount; p++) {
                        sendToServer(new long[][] { { 0 } }, (p + 1));
                    }

                    ret = true;
                    return "The files id/s provided by the servers is incorrect.";
                }
            }
        }
        return phase2Result;
    }

    /**
     * interpolate server data send as part of phase 1 computation
     * multithreaded across number of keywords
     *
     * @param threadNum
     */
    public void task11(int threadNum) {
        BigInteger interpolatedValue;
        int batchSize = (int) Math.ceil(keywordCount / (double) numThreads);
        BigInteger[] shares = new BigInteger[serverCount];
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > keywordCount) {
            end = keywordCount;
        }

        BigInteger value0 = BigInteger.valueOf(0);

        for (int i = start; i < end; i++) {
            for (int j = 0; j < serverCount; j++) {
                shares[j] = new BigInteger(objectsReceivedString1D[j][i]);
            }

            interpolatedValue = lagrangeInterpolation(shares);

            if (interpolatedValue.equals(value0)) {
                phase1Result = i;
            }
        }
    }

    /**
     * interpolate server data send as part of phase 1 computation during server
     * verification is true
     * multithreaded across number of keywords
     *
     * @param threadNum
     */
    public void task12(int threadNum) {
        int batchSize = (int) Math.ceil(keywordCount / (double) numThreads);
        int start = (threadNum - 1) * batchSize;
        int end = start + batchSize;
        if (end > keywordCount) {
            end = keywordCount;
        }
        BigInteger value0 = BigInteger.valueOf(0);
        BigInteger[] shares = new BigInteger[serverCount];
        for (int i = start; i < end; i++) {
            shares[0] = new BigInteger(objectsReceivedString1D[0][i]);
            shares[1] = new BigInteger(objectsReceivedString1D[1][i]);
            shares[2] = new BigInteger(objectsReceivedString1D[2][i]);
            servers_1_2_3[threadNum - 1] = lagrangeInterpolation(shares, 1, 2, 3);
            shares[0] = new BigInteger(objectsReceivedString1D[0][i]);
            shares[1] = new BigInteger(objectsReceivedString1D[1][i]);
            shares[2] = new BigInteger(objectsReceivedString1D[3][i]);
            servers_1_2_4[threadNum - 1] = lagrangeInterpolation(shares, 1, 2, 4);
            shares[0] = new BigInteger(objectsReceivedString1D[1][i]);
            shares[1] = new BigInteger(objectsReceivedString1D[2][i]);
            shares[2] = new BigInteger(objectsReceivedString1D[3][i]);
            servers_2_3_4[threadNum - 1] = lagrangeInterpolation(shares, 2, 3, 4);
            shares[0] = new BigInteger(objectsReceivedString1D[0][i]);
            shares[1] = new BigInteger(objectsReceivedString1D[2][i]);
            shares[2] = new BigInteger(objectsReceivedString1D[3][i]);
            servers_1_3_4[threadNum - 1] = lagrangeInterpolation(shares, 1, 3, 4);
            // checking if server values are consistent across pairs
            if (servers_1_2_3[threadNum - 1].equals(value0) || servers_1_2_4[threadNum - 1].equals(value0)
                    || servers_2_3_4[threadNum - 1].equals(value0) || servers_1_3_4[threadNum - 1].equals(value0)) {
                phase1Result = i;
                if (!(servers_1_2_3[threadNum - 1].equals(servers_1_2_4[threadNum - 1])
                        && servers_1_2_4[threadNum - 1].equals(servers_2_3_4[threadNum - 1])
                        && servers_2_3_4[threadNum - 1].equals(servers_1_3_4[threadNum - 1]))) {
                    flagCVP1 = false;
                    break;
                }
            }
        }
    }

    /**
     * For fetching the index of search keyword from ACT table
     *
     * @throws IOException
     */
    public String phase1() throws IOException {

        Instant startTime;
        Instant endTime;

        stage = "1";
        phase1Result = -1;
        serverCount = 3;
        // if no client verification, only three servers are required
        if (clientVerification) {
            serverCount = 4;
        }

        setClientVerification(clientVerification);
        // creating shares for the keyword
        BigInteger[] keywordShares = createShares(searchKeyword);

        // sending search keyword shares and client id to the server

        BigInteger[] data1D = new BigInteger[2];
        data1D[1] = new BigInteger(clientId);
        startTime = Instant.now();
        for (int i = 0; i < serverCount; i++) {
            if (i <= 2) {
                data1D[0] = keywordShares[Math.floorMod(i - 1, 3)];
            } else {
                data1D[0] = keywordShares[3];
            }
            sendToServer(data1D, (i + 1));
        }
        endTime = Instant.now();
        System.out.println("Time taken to send keyword shares to servers: "
                + Duration.between(startTime, endTime).toMillis() + " ms");

        // waiting for servers to send the search keyword index in ACT table

        startTime = Instant.now();
        List<Object> objects = startClient(serverCount);
        endTime = Instant.now();
        System.out.println("Time taken to receive keyword index shares from servers: "
                + Duration.between(startTime, endTime).toMillis() + " ms");

        // extracting search keyword indexes in ACT table
        type = "1D";
        objectsReceivedString1D = new String[serverCount][];
        readObjectsAsString(type, objects); // reading data sent by servers

        keywordCount = (objectsReceivedString1D[0].length - 1); // since last element stores the label for the server

        if (!clientVerification) { // off clientVerification
            stageLabel = "1";
            createThreads(stage, stageLabel); // running threads to interpolate the data
        } else {
            flagCVP1 = true;
            stageLabel = "2";
            createThreads(stage, stageLabel);
            if (!flagCVP1) {
                log.info("The access control results by the servers is incorrect.");

                for (int i = 0; i < serverCount; i++) {
                    sendToServer(new long[] { 0 }, (i + 1));
                }

                System.out.println("Setting ret true point 1");
                ret = true;
                return "The access control results by the servers is incorrect.";
            }
        }

        // to inform the servers to terminate if client has no access on the keyword or
        // does not wish to continue
        if (phase1Result == -1) {
            log.info("Client has no access on documents of keyword '" + searchKeyword + "'.");

            for (int i = 0; i < serverCount; i++) {
                sendToServer(new long[] { 0 }, (i + 1));
            }
            ret = true;
            return "Client has no access on documents of keyword '" + searchKeyword + "'.";
        }
        System.out.println("The index of keyword " + searchKeyword + " is " + (phase1Result + 1) + ".");
        return null;
    }

    /**
     * Performs initialization tasks like reading from the properties files, initial
     * memory allocation etc
     *
     * @throws IOException
     */
    public void initialization()
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        try (InputStream input = DocStarApplication.class.getClassLoader()
                .getResourceAsStream("config/Common.properties")) {
            if (input == null) {
                System.err.println("❌ ERROR: Common.properties not found in the classpath.");
                // You may want to throw an exception here
                return;
            }
            // Load the properties from the InputStream
            commonProperties.load(input);
            System.out.println("✅ Properties loaded successfully.");

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        serverVerification = Boolean.parseBoolean(commonProperties.getProperty("serverVerification"));

        clientVerification = false;
        numThreads = Integer.parseInt(commonProperties.getProperty("numThreads"));
        optRow = Integer.parseInt(commonProperties.getProperty("optRow"));
        optCol = Integer.parseInt(commonProperties.getProperty("optCol"));
        fileCount = Integer.parseInt(commonProperties.getProperty("fileCount"));
        checkpoint1 = Integer.parseInt(commonProperties.getProperty("checkpoint1"));

        if (serverVerification) {
            serverCount = 4;
        } else {
            serverCount = 3;
        }
        serverIP = new String[serverCount];
        serverPort = new int[serverCount];
        for (int i = 0; i < serverCount; i++) {
            serverIP[i] = commonProperties.getProperty("serverIP" + (i + 1));
            serverPort[i] = Integer.parseInt(commonProperties.getProperty("serverPort" + (i + 1)));
            serverConnections.put((i + 1), new ServerConnection((i + 1), serverIP[i], serverPort[i]));
        }

        // initialization the stemmer for english keywords
        stemmer = new englishStemmer();
    }

    // public static

    public Object runSearch(String kw, String id, boolean verification, boolean initialSearch)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException,
            InstantiationException, IllegalAccessException, InterruptedException {

        // to store the phase id
        int phase;

        // fetching all the search keywords
        // String fileName = "data/cleartext/keywords.txt";
        // String[] keywords = Helper.readFile(fileName);

        searchKeyword = kw;
        clientId = id;
        clientVerification = verification;

        // phase 1
        phase = 1;
        cleanUpPhaseData(phase);
        System.out.println("Start of Phase 1.");
        System.out.println("---------------------------------------");

        String result = phase1();
        if (ret) {
            ret = false;
            return result;
        }

        System.out.println("End of Phase 1.");
        cleanUpPhaseData(phase);

        // phase 2
        phase = 2;
        cleanUpPhaseData(phase);
        System.out.println("Start of Phase 2.");
        System.out.println("---------------------------------------");
        Object resultObj = phase2();
        System.out.println("End of Phase 2.");
        if (ret) {
            ret = false;
            return result;
        }

        return resultObj;
    }

    public void setFetchCount(int count) {
        fetchCount = count;
    }

    public void closeSocket() throws IOException {
        if (ss != null)
            ss.close();
    }

    public void setPhase2Result(ArrayList<Long> fileIDs) {
        phase2Result = fileIDs;
    }

    public ArrayList<?> getPhase2Result() {
        return phase2Result;
    }

    public void resetServers(Boolean verification, boolean initialSearch) throws IOException {

        for (int i = 0; i < serverCount; i++) {
            try {
                sendToServer("RESTART," + verification, (i + 1));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setClientVerification(Boolean verification) {
        for (int i = 0; i < serverCount; i++) {
            try {
                sendToServer(verification, (i + 1));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}