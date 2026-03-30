package com.docstar.middleware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.docstar.model.Document;
import com.docstar.model.dto.DBORequest;
import com.docstar.model.dto.FileShare;
import com.docstar.model.dto.FileUpload;
import com.docstar.model.dto.FilesDataStructureShares;
import com.docstar.model.dto.ResizeOptRequest;
import com.docstar.model.dto.SparseDelta;
import com.docstar.repository.DocumentRepository;
import com.docstar.utility.ExtractKeywordsUtil;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

@Service
public class DBOParallel extends Thread implements AutoCloseable {

    private final Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    @Autowired
    @Qualifier("dboProperties")
    Properties dboProperties;

    @Autowired
    @Qualifier("commonProperties")
    Properties commonProperties;

    @Autowired
    @Qualifier("outsourceProperties")
    Properties outsourceProperties;

    private int newFileId;
    private int serverCount;
    private long[][] keywordVectorShares;
    public int keywordCount;
    private int clientCount;
    private long[] keywordVector;
    private long seedDBO;

    // to store the number of files
    public int fileCount;
    // to store the index of the keyword in act table
    private int phase1Result;
    private ArrayList<Long> phase2Result;
    // private long[] hash_list;
    private long[][] phase3Result;
    private boolean serverVerification;
    private boolean clientVerification;
    private long[][] fileVectors;
    private long[][][] fileVectorsShares;
    private long[][] hash;
    private int numThreads;
    private String stage;
    private String stageLabel;
    // private boolean flagCVP1;
    private BigInteger[] servers_1_2;
    private BigInteger[] servers_2_3;
    private BigInteger[] servers_3_1;
    private ArrayList<Instant> comTime = new ArrayList<>();
    private ArrayList<Instant> waitTime = new ArrayList<>();
    // private Scanner console;
    private int fileLength;
    private long[][] addr;
    private BigInteger[] noAccessCode;

    // to stores server shares as long values for 1D array
    private long[][] serverSharesAsLong_1D;
    // to stores server shares as long values for 2D array
    private long[][][] serverSharesAsLong_2D;
    // to stores server shares as long values for 1D array
    private String[][] objectReadString1D;
    // to stores server shares as long values for 2D array
    private String[][][] serverSharesAsString_2D;

    private String[][] content;
    private ArrayList<Instant> removeTime;
    private ArrayList<Double> perServerTime;

    private String[] serverIP;
    private int[] serverPort;
    private ServerSocket ss;
    private long[] indexHashList;

    private BigInteger[][] singleDShares;
    private BigInteger phase1ResultAccess;
    private long[][] singleDSharesLong;
    private long[][][] doubleDSharesLong;
    private BigInteger[][][] doubleDSharesBig;
    private StringBuilder[][][] doubleDSharesBigString;
    private BigInteger[][] doubleDataBig;
    private StringBuilder[][] doubleDataBigString;
    private BigInteger[] singleData;
    private long[] singleDataLong;
    private long[][] doubleDataLong;

    // to store the numeric version of text
    private StringBuilder numericText;
    // to store the updates text
    private StringBuilder newText;

    private long[] optInvRowVec;
    private long[][] optInvRowVecShare;
    private int optRow;
    private int optCol;
    private int startingPos;
    private int countItems;
    private long[][][] optIndexShare;
    private long[][] serverVerificationPhase3;
    private long[] phase2Interpolation;
    private long[][][] verificationServer2DPhase3;
    private boolean freeSlot;

    int checkpoint1;
    int checkpoint2;

    @Autowired
    DocumentRepository documentRepository;

    /**
     * to perform hash digest of given data using SHA-1
     *
     * @param data The given data
     * @return The numeric hash digest value of 20B
     * @throws NoSuchAlgorithmException
     */
    private String hashDigest(String data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(data.getBytes());
        byte[] digest = md.digest();
        BigInteger result = Helper.mod(new BigInteger(digest));
        return result.toString();
    }

    /**
     * Convert keyword to number
     *
     * @param data the keyword
     * @return the numeric format of keyword
     */
    public StringBuilder keywordToNumber(String data) {
        numericText = new StringBuilder("");
        for (int i = 0; i < data.length(); i++) {
            numericText.append((int) data.charAt(i) - 86);
        }
        return numericText;
    }

    /**
     * To convert numeric string to string values
     *
     * @param numericString the numeric format of string
     * @return the cleartext string
     */
    private String decrypt_numeric_string(String numericString) {
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
     * To interpolate string values
     *
     * @param share the string value of the shares
     * @return the cleartext/interpolated value
     */
    private BigInteger lagrangeInterpolationAsBigInteger(BigInteger share[]) {

        return switch (share.length) {
            case 2 -> Helper.mod(Helper.mod(BigInteger.valueOf(2).multiply(share[0]))
                    .subtract(share[1]));
            case 3 -> Helper.mod(Helper.mod(Helper.mod(BigInteger.valueOf(3)
                    .multiply(share[0])).subtract(Helper.mod(
                            BigInteger.valueOf(3)
                                    .multiply(share[1]))))
                    .add(share[2]));
            default -> throw new IllegalStateException("Unexpected value: " + share.length);
        };
    }

    /**
     * To interpolate long values
     *
     * @param share the long value of the shares
     * @return the cleartext/interpolated value
     */
    private long lagrangeInterpolationAsLong(long share[]) {
        return switch (share.length) {
            case 2 -> Helper.mod(Helper.mod(2 * share[0]) - share[1]);
            case 3 -> Helper.mod((Helper.mod(Helper.mod(3 * share[0]) -
                    Helper.mod(3 * share[1]))) + share[2]);
            default -> throw new IllegalStateException("Unexpected value: " + share.length);
        };
    }

    /**
     * Reads responses from the first 'count' servers using persistent streams.
     * * @param count The number of servers to read from (e.g., 3 or 4)
     */
    private List<Object> startClient(int count) throws IOException, ClassNotFoundException {
        List<Object> objects = new ArrayList<>();

        // Assuming standard operation requires responses from Server 1 to 'count'
        for (int i = 1; i <= count; i++) {
            DBOConnection conn = connections.get(i);
            if (conn != null) {
                try {
                    // Blocking read from existing stream
                    Object response = conn.receive();
                    objects.add(response);
                } catch (IOException e) {
                    System.err.println("Error reading from Server " + i);
                    throw e;
                }
            }
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
    private void sendToServer(Object data, int serverId) throws IOException {
        DBOConnection conn = connections.get(serverId);
        if (conn == null) {
            throw new IOException("No active connection to Server " + serverId);
        }
        try {
            // Create wrapper
            DBORequest request = new DBORequest(data);

            // Write to existing stream// Ensure thread safety if DBO becomes multi-threaded
            conn.send(request);
        } catch (

        IOException e) {
            System.err.println("Error sending to Server " + serverId);
            throw e;
        }
    }

    /**
     * To read values from objects as long
     *
     * @param type the type of object as 1D or 2D
     */
    private void readObjectsAsLong_1D(List<Object> objects) {

        for (int i = 0; i < objects.size(); i++) {
            int temp = (int) (((long[]) objects.get(i))[((long[]) objects.get(i)).length - 1]);
            serverSharesAsLong_1D[temp - 1] = ((long[]) objects.get(i));
        }
    }

    private void readObjectsAsLong_2D(List<Object> objects) {
        for (int i = 0; i < objects.size(); i++) {
            int temp = (int) (((long[][]) objects.get(i))[((long[][]) objects.get(i)).length - 1][0]);
            serverSharesAsLong_2D[temp - 1] = ((long[][]) objects.get(i));
        }
    }

    /**
     * To read values from objects as long
     *
     * @param type the type of object as 1D
     */
    private void readObjectsAsString_1D(List<Object> objects) {
        for (int i = 0; i < objects.size(); i++) {
            String objectRead = new String((byte[]) objects.get(i), StandardCharsets.UTF_8);

            // Split by pipe to separate data from server label
            String[] parts = objectRead.split("\\|");
            int temp = Integer.parseInt(parts[1]);

            // Now split the data part by commas
            String[] dataParts = parts[0].split(",");

            // Store the data
            objectReadString1D[temp - 1] = dataParts;
        }
    }

    /**
     * To read values from objects as long
     *
     * @param type the type of object 2D
     */
    private void readObjectsAsString_2D(List<Object> objects) {

        for (int i = 0; i < objects.size(); i++) {
            byte[][] data = (byte[][]) objects.get(i);
            int temp = new String(data[data.length - 1], StandardCharsets.UTF_8).charAt(0) - '0';
            // System.out.println("Server nu:" + temp);
            serverSharesAsString_2D[temp - 1] = new String[phase2Result.size()][];
            for (int j = 0; j < data.length - 1; j++) {
                serverSharesAsString_2D[temp - 1][j] = new String(data[j], StandardCharsets.UTF_8).split(",");
            }
        }
    }

    /**
     * Create share for secret value as string
     *
     * @param secret      the secret value as tring
     * @param serverCount the number of servers for which shares are to be created
     * @return the share of the secret value as string is returned for given number
     *         of server as servercount
     */
    private BigInteger[] shamirSecretSharingAsBigInt(StringBuilder secret, int serverCount) {

        BigInteger secretBig = new BigInteger(String.valueOf(secret));
        BigInteger[] share = new BigInteger[serverCount];

        long slope = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(Constant.getMaxRandomBound() - Constant.getMinRandomBound()) +
                Constant.getMinRandomBound();

        // check if the secret size is enough for long or BigInteger calculation
        for (int i = 0; i < serverCount; i++) {
            share[i] = Helper
                    .mod(Helper.mod(BigInteger.valueOf(i + 1).multiply(BigInteger.valueOf(slope))).add(secretBig));
        }

        return share;
    }

    /**
     * Create share for secret value as string
     *
     * @param secret      the secret value as tring
     * @param serverCount the number of servers for which shares are to be created
     * @return the share of the secret value as string is returned for given number
     *         of server as servercount
     */
    private long[] shamirSecretSharingAsLong(long secret, int serverCount) {
        long[] share = new long[serverCount];

        // choosing the slope value for line
        long slope = java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(Constant.getMaxRandomBound() - Constant.getMinRandomBound()) +
                Constant.getMinRandomBound();

        // check if the secret size is enough for long or BigInteger calculation
        for (int i = 0; i < serverCount; i++) {
            share[i] = Helper.mod(Helper.mod(Helper.mod(i + 1) * slope)
                    + secret);
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
        // stemmer.setCurrent(searchKeyword);
        // stemmer.stem();
        // searchKeyword = stemmer.getCurrent();

        // converting keyword as string to number
        keywordToNumber(searchKeyword);
        // padding token to avoid leakage due to keyword size
        if (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
            while (numericText.length() < 2 * Constant.getMaxKeywordLength())
                numericText.append(Constant.getPadding());
        }
        // creating the shares for each character in the keyword
        return shamirSecretSharingAsBigInt(numericText, serverCount);
    }

    private long[] getHashBlocks(String hash) {
        long[] temp = new long[Constant.getHashBlockCount()];
        int j = 0;
        for (int i = 0; i < hash.length(); i = i + Constant.getHashBlockSize()) {
            int end = i + Constant.getHashBlockSize();
            if (end > hash.length())
                end = hash.length();

            temp[j] = Long.parseLong(hash.substring(i, end));
            j++;
        }
        return temp;
    }

    private void updateAddrData(Boolean flag) throws NoSuchAlgorithmException, IOException, ClassNotFoundException {
        if (flag) { // if free slot avaliable

            // changing count of items
            doubleDataLong = new long[keywordCount][2 + 2 * Constant.getHashBlockCount()];
            doubleDataLong[phase1Result][1] = 1;

            // change of hash digest for start and count

            String hashAC = hashDigest(hashDigest(String.valueOf(startingPos)) + (countItems + 1));

            long[] temp = getHashBlocks(hashAC);
            // finding difference between hash blocks
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                doubleDataLong[phase1Result][2 + i] = Helper.mod(temp[i] - indexHashList[i]);
            }

            // change of hash digest for position vector
            hashAC = hashDigest(String.valueOf((startingPos + countItems) + 1));
            // System.out.println("after:" + hashAC);
            temp = getHashBlocks(hashAC);
            // finding difference between hash blocks
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                doubleDataLong[phase1Result][2 + Constant.getHashBlockCount() + i] = Helper
                        .mod(temp[i] - indexHashList[Constant.getHashBlockCount() + i]);
            }

            doubleDSharesLong = new long[serverCount][keywordCount][2 + 2 * Constant.getHashBlockCount()];

            task03();

            // sending search keyword shares and client id to the server
            long[][] data2D;
            for (int i = 0; i < serverCount; i++) {
                data2D = doubleDSharesLong[Math.floorMod(i - 1, 3)];
                sendToServer(data2D, (i + 1));
            }

        } else { // free slot unavailable
            // System.out.println("no free slot addr");

            long[][] data2D;
            for (int i = 0; i < serverCount; i++) {
                data2D = new long[][] { { 0 } };
                sendToServer(data2D, (i + 1));
            }

            List<Object> objects = startClient(serverCount);

            serverSharesAsLong_2D = new long[serverCount][][];

            readObjectsAsLong_2D(objects);

            doubleDataLong = new long[keywordCount][2 + 2 * Constant.getHashBlockCount()];
            task05();

            // update entire addr list
            addr = new long[keywordCount][2 + 2 * Constant.getHashBlockCount()];
            task06();

            // send to server
            doubleDSharesLong = new long[serverCount][keywordCount][2 + 2 * Constant.getHashBlockCount()];
            doubleDataLong = addr;
            task03();

            // sending search keyword shares and client id to the server
            for (int i = 0; i < serverCount; i++) {
                data2D = doubleDSharesLong[Math.floorMod(i - 1, 3)];
                sendToServer(data2D, (i + 1));
            }
        }
    }

    public void updateOptValues(long targetFileId, boolean isAdd, String searchKeyword)
            throws IOException, NoSuchAlgorithmException {
        System.out.println("\n=== FULL MATRIX BLIND UPDATE (" + (isAdd ? "ADD" : "DELETE") + ") ===");

        // ====================================================================
        // STEP 1: Calculate New Hash Blocks
        // ====================================================================
        long[] newHashBlocks;

        if (isAdd) {
            StringBuilder hDigestS = new StringBuilder("");
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                StringBuilder temp = new StringBuilder(String.valueOf(phase2Interpolation[i]));
                while (temp.length() < Constant.getHashBlockSize()) {
                    temp.insert(0, "0");
                }
                hDigestS.append(temp);
            }
            String hDigestC = hashDigest(String.valueOf(hDigestS.append(targetFileId)));
            newHashBlocks = getHashBlocks(hDigestC);
        } else {
            String hDigestC = hashDigest(searchKeyword);
            for (int i = Constant.getHashBlockCount(); i < countItems; i++) {
                long existingFileId = phase2Interpolation[i];
                if (existingFileId != targetFileId && existingFileId > 0) {
                    hDigestC = hashDigest(hDigestC + existingFileId);
                }
            }
            newHashBlocks = getHashBlocks(hDigestC);
        }

        // ====================================================================
        // STEP 2: Build the Full Database Delta Array
        // ====================================================================
        // Create an array the size of the ENTIRE database (defaults to 0s)
        long[] fullDatabaseDelta = new long[optRow * optCol];

        // A. Insert Hash Block Deltas
        for (int i = 0; i < Constant.getHashBlockCount(); i++) {
            int pos = startingPos + i;
            fullDatabaseDelta[pos] = Helper.mod(newHashBlocks[i] - phase2Interpolation[i]);
        }

        // B. Insert File ID Delta
        if (isAdd) {
            if (((startingPos % optCol) < ((startingPos + countItems) % optCol))
                    && phase2Interpolation[countItems] == 0) {
                freeSlot = true;
                int filePos = startingPos + countItems;
                fullDatabaseDelta[filePos] = targetFileId;
            } else {
                freeSlot = false;
                System.out.println("No free slot available");
                for (int s = 0; s < serverCount; s++) {
                    sendToServer(new long[] { 0 }, (s + 1)); // Send termination array
                }
                return;
            }
        } else {
            boolean found = false;
            for (int i = Constant.getHashBlockCount(); i < countItems; i++) {
                if (phase2Interpolation[i] == targetFileId) {
                    int filePos = startingPos + i;
                    fullDatabaseDelta[filePos] = Helper.mod(-targetFileId);
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println("File ID " + targetFileId + " not found for deletion.");
                for (int s = 0; s < serverCount; s++) {
                    sendToServer(new long[] { 0 }, (s + 1));
                }
                return;
            }
        }

        // ====================================================================
        // STEP 3: Create Shares and Send (Multithreaded)
        // ====================================================================
        System.out.println("Generating secret shares for entire " + fullDatabaseDelta.length + " slot database...");

        singleDataLong = fullDatabaseDelta;
        singleDSharesLong = new long[serverCount][optRow * optCol];

        // Use your existing multithreading logic to handle the massive array
        task02();

        for (int s = 0; s < serverCount; s++) {
            sendToServer(singleDSharesLong[s], (s + 1));
        }
    }

    public void getKeywordIndex(String searchKeyword) throws IOException, ClassNotFoundException {
        // to store the stage

        phase1Result = -1;
        serverCount = 3;
        // creating shares for the keyword
        BigInteger[] keywordShares = createShares(searchKeyword);

        // System.out.println(searchKeyword);
        // sending search keyword shares and client id to the server
        BigInteger[] data1D = new BigInteger[1];
        for (int i = 0; i < serverCount; i++) {
            // System.out.println(keywordShares[Math.floorMod(i - 1, 3)]);
            data1D[0] = keywordShares[Math.floorMod(i - 1, 3)];
            sendToServer(data1D, (i + 1));
        }

        // waiting for servers to send the search keyword index in ACT table
        List<Object> objects = startClient(serverCount);
        // extracting search keyword indexes in ACT table
        serverCount = 3;

        objectReadString1D = new String[serverCount][];

        readObjectsAsString_1D(objects); // reading data sent by servers

        if (objectReadString1D[0][0].isEmpty()) {
            keywordCount = 0;
        } else {
            keywordCount = objectReadString1D[0].length;
        }

        task11(); // running threads to interpolate the data
    }

    private void readAddrTable() {

        long interpolatedValue;
        long[] shares = new long[serverCount];

        for (int i = 0; i < serverSharesAsLong_1D[0].length - 1; i++) {
            for (int j = 0; j < serverCount; j++) {
                shares[j] = serverSharesAsLong_1D[j][i];
            }
            interpolatedValue = lagrangeInterpolationAsLong(shares);
            if (i == 0) {
                startingPos = (int) interpolatedValue;
            } else if (i == 1) {
                countItems = (int) interpolatedValue;
            } else {
                indexHashList[i - 2] = interpolatedValue;
            }
        }
    }

    private void getAddrData() throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        serverCount = 3;

        // preparing addr/keyword vector share for sending to server to retrieve the
        // addr data for the keyword
        keywordVector = new long[keywordCount];
        keywordVector[phase1Result] = 1;
        keywordVectorShares = new long[serverCount][keywordCount];

        task21();

        long[] data1D; // sending share to the server
        for (int i = 0; i < serverCount; i++) {
            data1D = keywordVectorShares[Math.floorMod(i - 1, 3)];
            sendToServer(data1D, (i + 1));
        }

        List<Object> objects = startClient(serverCount);

        serverSharesAsLong_1D = new long[serverCount][];
        readObjectsAsLong_1D(objects);

        // process the data received from server to get the file ids
        // get position in opt_inv, number of files for keyword and hash values from
        // addr table
        indexHashList = new long[2 * Constant.getHashBlockCount()];
        readAddrTable();
    }

    private void phase2ResultInterpolation() {

        long[] shares = new long[serverCount];
        // System.out.println(optCol);
        for (int i = 0; i < optCol; i++) {
            // System.out.println(i);
            for (int j = 0; j < serverCount; j++) {
                shares[j] = serverSharesAsLong_1D[j][i];
            }
            phase2Interpolation[i] = lagrangeInterpolationAsLong(shares);
            // System.out.print(phase2Interpolation[i] + " ");
        }
        // System.out.println();
    }

    public void getOptValues() throws IOException, ClassNotFoundException {

        // create vector to extract file_ids from opt_inv and another vector for
        // extracting only particular columns
        optInvRowVec = new long[optRow];
        optInvRowVec[startingPos / optCol] = 1;

        optInvRowVecShare = new long[serverCount][optRow];
        optIndexShare = new long[serverCount][1][optRow];

        task22();

        long[][] data2D; // sending share to the server
        for (int i = 0; i < serverCount; i++) {
            optIndexShare[i][0] = optInvRowVecShare[i];
            data2D = optIndexShare[i];
            sendToServer(data2D, (i + 1));
        }

        List<Object> objects = startClient(serverCount);

        serverSharesAsLong_1D = new long[serverCount][];
        readObjectsAsLong_1D(objects);

        phase2Result = new ArrayList<>();

        phase2Interpolation = new long[optCol];
        phase2ResultInterpolation();
    }

    public String addFile(String fileContent, String[] keywords)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        System.out.println("\n=== ADDING NEW FILE ===");
        System.out.println("File content length: " + fileContent.length());
        System.out.println("Keywords: " + Arrays.toString(keywords));

        serverCount = 3;

        // Step 1: Check which keywords are new
        List<String> newKeywords = new ArrayList<>();
        List<Integer> existingKeywordIndices = new ArrayList<>();

        System.out.println("Current keyword count: " + keywordCount);

        if (keywordCount > 0) {
            // There are existing keywords - check which ones exist
            for (String keyword : keywords) {
                for (int i = 0; i < serverCount; i++) {
                    sendToServer("GET_KEYWORD_INDEX", (i + 1));
                }
                getKeywordIndex(keyword);

                if (phase1Result == -1) {
                    newKeywords.add(keyword);
                } else {
                    existingKeywordIndices.add(phase1Result);
                }
            }
        } else {
            // No existing keywords - all are new
            System.out.println("No existing keywords in system. All keywords are new.");
            newKeywords.addAll(Arrays.asList(keywords));
        }

        // Step 2: Add new keywords
        if (!newKeywords.isEmpty()) {
            System.out.println("Adding " + newKeywords.size() + " new keywords...");
            serverCount = 4;

            for (String keyword : newKeywords) {
                addNewKeyword(keyword);
            }

            serverCount = 3;

            // Get their indices after adding
            for (String keyword : newKeywords) {
                for (int i = 0; i < serverCount; i++) {
                    sendToServer("GET_KEYWORD_INDEX", (i + 1));
                }
                getKeywordIndex(keyword);

                if (phase1Result == -1) {
                    System.err.println("ERROR: Keyword '" + keyword + "' not found after adding!");
                    return "KEYWORD_ADD_FAILED";
                }

                existingKeywordIndices.add(phase1Result);
            }
        }

        // Verify we have all keyword indices
        if (existingKeywordIndices.size() != keywords.length) {
            System.err.println("ERROR: Keyword count mismatch. Expected " + keywords.length +
                    ", got " + existingKeywordIndices.size());
            return "KEYWORD_INDEX_MISMATCH";
        }

        // Step 3: Encrypt file content
        long[] encryptedContent = encryptFile(fileContent);
        fileCount = getFileCount();
        int newFileId = fileCount + 1;

        // Step 4: Create keyword list
        int pad;
        if (keywords.length <= checkpoint2) {
            pad = checkpoint2 - keywords.length;
        } else {
            pad = checkpoint1 - keywords.length;
        }

        // Total length = hash blocks + keywords + padding
        int keywordListLength = Constant.getHashBlockCount() + keywords.length + pad;
        long[] keywordIndices = new long[keywordListLength];

        // Add hash blocks first
        String fileHashDigest = hashDigest(fileContent);
        long[] fileHashBlocks = getHashBlocks(fileHashDigest);
        for (int i = 0; i < Constant.getHashBlockCount(); i++) {
            keywordIndices[i] = fileHashBlocks[i];
        }

        // Add keyword indices after hash blocks
        for (int i = 0; i < keywords.length; i++) {
            keywordIndices[Constant.getHashBlockCount() + i] = existingKeywordIndices.get(i) + 1;
        }

        System.out.println("Keyword indices for file: " + Arrays.toString(
                Arrays.copyOfRange(keywordIndices, Constant.getHashBlockCount(),
                        Constant.getHashBlockCount() + keywords.length)));

        // Step 5: Send ADD_FILE command ONCE
        for (int i = 0; i < serverCount; i++) {
            sendToServer("ADD_FILE_CONTENT", (i + 1));
        }

        // Send file content shares
        long[][][] fileContentShares = createShares2D(encryptedContent);
        for (int i = 0; i < serverCount; i++) {
            sendToServer(fileContentShares[Math.floorMod(i - 1, 3)][0], (i + 1));
        }

        // Send keyword list shares
        long[][][] keywordListShares = createShares2D(keywordIndices);
        for (int i = 0; i < serverCount; i++) {
            sendToServer(keywordListShares[Math.floorMod(i - 1, 3)][0], (i + 1));
        }

        // Step 6: Add file ID to EACH keyword's inverted index
        System.out.println("\n=== ADDING FILE TO INVERTED INDICES ===");
        for (String keyword : keywords) {
            System.out.println("Adding file " + newFileId + " to keyword '" + keyword + "'");
            addFilesOpt(keyword, String.valueOf(newFileId));
        }

        fileCount++;
        System.out.println("\n✓✓✓ FILE ADDED SUCCESSFULLY ✓✓✓");
        System.out.println("File ID: " + newFileId);

        return "SUCCESS";
    }

    // Helper
    private long[][][] createShares2D(long[] data) {
        long[][][] shares = new long[serverCount][1][data.length];
        for (int i = 0; i < data.length; i++) {
            long[] itemShares = shamirSecretSharingAsLong(data[i], serverCount);
            for (int j = 0; j < serverCount; j++) {
                shares[j][0][i] = itemShares[j];
            }
        }
        return shares;
    }

    // ============================================================================
    // DBO-SIDE UPDATE: Add dimension sending to addNewKeywordsBatch
    // ============================================================================

    public String addNewKeywordsBatch(String[] keywords)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH KEYWORD ADDITION: " + keywords.length + " keywords");
        System.out.println("=".repeat(80));

        List<BigInteger> noAccessCodesNew = new ArrayList<>();

        // Step 1: Check if any keywords exist

        serverCount = 4;

        for (String keyword : keywords) {
            for (int i = 0; i < serverCount; i++) {
                sendToServer("GET_KEYWORD_INDEX", (i + 1));
            }
            getKeywordIndex(keyword);

            if (phase1Result != -1) {
                System.out.println("Keyword '" + keyword + "' already exists at index " + phase1Result);
                for (int i = 0; i < serverCount; i++) {
                    sendToServer(new byte[] { 0 }, (i + 1));
                }
                return "KEYWORD_EXISTS";
            }
        }

        serverCount = 3;

        for (int i = 0; i < serverCount; i++) {
            sendToServer("ADD_KEYWORDS_BATCH", (i + 1));
        }

        // Step 2: Build ACT data for all keywords
        System.out.println("\nBuilding ACT entries for " + keywords.length + " keywords...");

        StringBuilder batchData = new StringBuilder();
        batchData.append(keywords.length).append("|");

        for (int kwIdx = 0; kwIdx < keywords.length; kwIdx++) {
            String keyword = keywords[kwIdx];

            // Encode keyword
            keywordToNumber(keyword);
            if (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
                while (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
                    numericText.append(Constant.getPadding());
                }
            }

            BigInteger keywordBig = new BigInteger(numericText.toString());
            BigInteger position = BigInteger.valueOf(keywordCount + kwIdx);

            // Generate no access code
            BigInteger noAccessCode = generateNoAccessCodeForKeyword();

            keywordCount++;

            noAccessCodesNew.add(noAccessCode);

            // Build ACT row: [keyword, position, client1_access, client2_access, ...]
            batchData.append(keywordBig).append(",");
            batchData.append(position).append(",");

            for (int c = 0; c < clientCount; c++) {
                batchData.append(noAccessCode);
                if (c < clientCount - 1) {
                    batchData.append(",");
                }
            }

            if (kwIdx < keywords.length - 1) {
                batchData.append("|");
            }

            System.out.println("  Keyword " + (kwIdx + 1) + ": " + keyword);
        }

        // Step 3: Send ACT batch data to all servers
        byte[] batchBytes = batchData.toString().getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < serverCount; i++) {
            sendToServer(batchBytes, (i + 1));
        }

        System.out.println("✓ ACT batch data sent to all servers");

        // Step 4: Receive optRow and last ADDR from 3 servers
        serverCount = 3;
        List<Object> objects = startClient(serverCount);

        long[][] lastAddrFromServers = new long[serverCount][];
        long[] optRowFromServers = new long[serverCount];

        for (int i = 0; i < serverCount; i++) {
            long[] response = (long[]) objects.get(i);
            optRowFromServers[i] = response[0];

            lastAddrFromServers[i] = new long[response.length - 2];
            System.arraycopy(response, 1, lastAddrFromServers[i], 0, response.length - 2);

            System.out.println("Server " + (i + 1) + " optRow: " + optRowFromServers[i]);
        }

        // Interpolate last ADDR to get actual values
        int addrEntryLength = lastAddrFromServers[0].length;
        long[] lastAddr = new long[addrEntryLength];

        for (int i = 0; i < addrEntryLength; i++) {
            long[] shares = new long[3];
            for (int s = 0; s < 3; s++) {
                shares[s] = lastAddrFromServers[s][i];
            }
            lastAddr[i] = lagrangeInterpolation(shares);
        }

        long currentStartPos = lastAddr[0];
        long currentCount = lastAddr[1];
        long nextStartPos = currentStartPos + 50008;

        System.out.println("\nLast keyword position: startPos=" + currentStartPos +
                ", count=" + currentCount);
        System.out.println("Next available position: " + nextStartPos);

        // ✅ TRACK MAXIMUM POSITION
        long maxPosition = nextStartPos;

        // Step 5: Calculate and send ADDR for each new keyword
        System.out.println("\nCalculating ADDR entries...");

        for (int kwIdx = 0; kwIdx < keywords.length; kwIdx++) {
            String keyword = keywords[kwIdx];

            // For new keywords, they have no files yet, so count = hashBlockCount only
            long startPos = nextStartPos;
            long count = Constant.getHashBlockCount();

            // ✅ UPDATE MAX POSITION
            maxPosition = Math.max(maxPosition, startPos + count);

            System.out.println("\nKeyword " + (kwIdx + 1) + ": " + keyword);
            System.out.println("  startPos: " + startPos);
            System.out.println("  count: " + count);

            // Build ADDR entry
            int addrLength = 2 + 2 * Constant.getHashBlockCount();
            long[] addr = new long[addrLength];
            addr[0] = startPos;
            addr[1] = count;

            // Hash of (startPos, count)
            String hashAC = hashDigest(hashDigest(String.valueOf(startPos)) + count);
            long[] hashBlocksAC = getHashBlocks(hashAC);
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                addr[2 + i] = hashBlocksAC[i];
            }

            // Hash of positions (empty for new keyword)
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                addr[2 + Constant.getHashBlockCount() + i] = 0;
            }

            // Create shares
            long[][] addrShares = new long[3][addrLength];
            for (int i = 0; i < addrLength; i++) {
                long[] shares = shamirSecretSharingAsLong(addr[i], 3);
                for (int s = 0; s < 3; s++) {
                    addrShares[s][i] = shares[s];
                }
            }

            // Send to servers
            for (int s = 0; s < 3; s++) {
                int shareIndex;
                if (s == 0)
                    shareIndex = 2;
                else if (s == 1)
                    shareIndex = 0;
                else
                    shareIndex = 1;

                sendToServer(addrShares[shareIndex], (s + 1));
            }

            // Update for next keyword
            nextStartPos = startPos + 50008;
        }

        System.out.println("\n✓ All ADDR entries sent");

        // ✅ STEP 6: CALCULATE AND SEND NEW DIMENSIONS
        System.out.println("\n=== CALCULATING NEW DIMENSIONS ===");
        System.out.println("Max position used: " + maxPosition);
        System.out.println("Current optCol: " + this.optCol);

        // Calculate required optRow (ceiling division)
        int newOptRow = (int) ((maxPosition + this.optCol - 1) / this.optCol);

        System.out.println("Required optRow: " + newOptRow);
        System.out.println("Current optRow: " + this.optRow);

        // Use the larger value
        newOptRow = Math.max(newOptRow, this.optRow);

        // ✅ SEND DIMENSION UPDATE TO SERVERS
        System.out.println("\nSending dimension update to servers...");
        for (int s = 0; s < 3; s++) {
            int[] dimensions = { newOptRow, this.optCol };
            sendToServer(dimensions, (s + 1));
            System.out.println("  Sent to server " + (s + 1) + ": optRow=" + newOptRow +
                    ", optCol=" + this.optCol);
        }

        // ✅ UPDATE LOCAL DIMENSIONS
        this.optRow = newOptRow;

        System.out.println("\n✓ Dimensions updated locally: optRow=" + this.optRow +
                ", optCol=" + this.optCol);

        generateNoAccessCodes();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BATCH KEYWORD ADDITION COMPLETE");
        System.out.println("=".repeat(80));
        System.out.println("Keywords added: " + keywords.length);
        System.out.println("Total keywords: " + keywordCount);
        System.out.println("Final dimensions: optRow=" + this.optRow + ", optCol=" + this.optCol);
        System.out.println("=".repeat(80));

        return "SUCCESS";
    }

    /**
     * Generate noAccessCode using current keywordCount as seed
     * IMPORTANT: Call this BEFORE incrementing keywordCount!
     */
    private BigInteger generateNoAccessCodeForKeyword() {
        BigInteger range = Constant.getMaxLimitNoAccess()
                .subtract(Constant.getMinLimitNoAccess());
        int len = Constant.getMaxLimitNoAccess().bitLength();

        Random randNum = new Random(seedDBO + keywordCount + 1);

        // System.out.println("Seed: " + (seedDBO + keywordCount + 1));

        BigInteger noAccessCode = new BigInteger(len, randNum);

        if (noAccessCode.compareTo(Constant.getMinLimitNoAccess()) < 0)
            noAccessCode = noAccessCode.add(Constant.getMinLimitNoAccess());
        else if (noAccessCode.compareTo(range) >= 0)
            noAccessCode = noAccessCode.mod(range)
                    .add(Constant.getMinLimitNoAccess());

        return noAccessCode;
    }

    public String addNewClient() throws IOException, NoSuchAlgorithmException, ClassNotFoundException {
        System.out.println("\n=== ADDING NEW CLIENT ===");

        serverCount = 4;

        // Send command to servers
        for (int i = 0; i < serverCount; i++) {
            sendToServer("ADD_CLIENT", (i + 1));
        }

        // If there are existing keywords, need to create no-access shares for them
        if (keywordCount > 0) {
            // Create no-access shares for all existing keywords
            BigInteger[][] noAccessShares = new BigInteger[keywordCount][serverCount];

            for (int k = 0; k < keywordCount; k++) {
                noAccessShares[k] = shamirSecretSharingAsBigInt(
                        new StringBuilder(noAccessCode[k].toString()), serverCount);
            }

            // Send to each server
            for (int s = 0; s < serverCount; s++) {
                StringBuilder accessData = new StringBuilder();

                int shareIndex = (s == 0) ? 2 : (s == 1) ? 0 : (s == 2) ? 1 : 3;

                for (int k = 0; k < keywordCount; k++) {
                    accessData.append(noAccessShares[k][shareIndex]).append(",");
                }

                sendToServer(accessData.toString().getBytes(StandardCharsets.UTF_8),
                        (s + 1));
            }
        } else {
            // No keywords yet, send empty signal
            for (int i = 0; i < serverCount; i++) {
                sendToServer(new byte[] { 0 }, (i + 1));
            }
        }

        // Wait for confirmation
        List<Object> objects = startClient(4);

        String result = "SUCCESS";
        for (int i = 0; i < objects.size(); i++) {
            byte[] response = (byte[]) objects.get(i);
            String responseStr = new String(response, StandardCharsets.UTF_8);
            if (!responseStr.equals("SUCCESS")) {
                result = "FAILED";
                break;
            }
        }

        if (result.equals("SUCCESS")) {
            clientCount++;

            System.out.println("✓✓✓ CLIENT ADDED SUCCESSFULLY ✓✓✓");
            System.out.println("Client count: " + clientCount);
        }

        return result;
    }

    // In DBO.java - Add these helper methods
    private void convertToNumeric(String text) {
        text = text.toLowerCase();
        numericText = new StringBuilder();
        newText = new StringBuilder();
        char letter;
        int asciiValue;

        for (int i = 0; i < text.length(); i++) {
            letter = text.charAt(i);
            asciiValue = (int) letter;

            // Only special characters, alphabets and numbers
            if (32 <= asciiValue && asciiValue <= 126) {
                // Convert all ascii values to two digits
                if (asciiValue == 96)
                    numericText.append("10");
                else if (97 <= asciiValue && asciiValue <= 122)
                    numericText.append(asciiValue - 32);
                else if (123 <= asciiValue)
                    numericText.append(asciiValue - 112);
                else
                    numericText.append(asciiValue);

                newText.append(letter);
            }
        }
    }

    private List<String> createStringBlocks(String data) {
        String substring;
        List<String> result = new ArrayList<>();
        int end;

        for (int i = 0; i < data.length(); i += Constant.getHashBlockSize()) {
            end = i + Constant.getHashBlockSize();
            if (end >= data.length())
                end = data.length();
            substring = data.substring(i, end);
            result.add(substring);
        }
        return result;
    }

    // Now the encryption method
    private long[] encryptFile(String content) throws NoSuchAlgorithmException {

        // Step 1: Convert to numeric
        convertToNumeric(content);

        // Step 2: Create file vector
        List<String> fileVector = new ArrayList<>();

        // Add hash blocks first
        fileVector.addAll(createStringBlocks(hashDigest(String.valueOf(newText))));
        while (fileVector.size() < Constant.getHashBlockCount()) {
            fileVector.add("0");
        }

        // Add file content blocks
        fileVector.addAll(createStringBlocks(String.valueOf(numericText)));

        // Step 3: Convert to long array
        long[] result = new long[fileVector.size()];
        for (int i = 0; i < fileVector.size(); i++) {
            result[i] = Long.parseLong(fileVector.get(i));
        }

        return result;
    }

    /**
     * to add new files with existing keywords
     *
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public void addFilesOpt(String searchKeyword, String fileID)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {
        serverCount = 3;

        // for (int i = 0; i < serverCount; i++) {
        // sendToServer("GET_OPT_DIMENSIONS", (i+1));
        // }

        for (int i = 0; i < serverCount; i++) {
            sendToServer("ADD_FILE_TO_KEYWORD", (i + 1));
        }

        // Receive dimensions
        List<Object> objects = startClient(serverCount);

        int[][] dimensions = new int[serverCount][];
        for (int i = 0; i < serverCount; i++) {
            dimensions[i] = (int[]) objects.get(i);
        }

        // Update local values
        this.optRow = dimensions[0][0];
        this.optCol = dimensions[0][1];

        newFileId = Integer.parseInt(fileID);
        // get keyword index
        getKeywordIndex(searchKeyword);

        // to inform the servers to terminate if client has no access on the keyword or
        // does not wish to continue
        if (phase1Result == -1) {
            log.info("Not found '" + searchKeyword + "'.");

            for (int i = 0; i < serverCount; i++) {
                sendToServer(new long[] { 0 }, (i + 1));
            }
            return;
        }
        // System.out.println("The index of keyword " + searchKeyword + " is " +
        // (phase1Result + 1) + ".");

        getAddrData();

        getOptValues();

        // ===== CHECK FOR DUPLICATE =====
        System.out.println("\n=== CHECKING FOR DUPLICATE ===");
        boolean fileExists = false;

        // File IDs start after hash blocks
        for (int i = Constant.getHashBlockCount(); i < countItems; i++) {
            long existingFileId = phase2Interpolation[i];

            if (existingFileId == newFileId) {
                fileExists = true;
                break;
            }
        }

        if (fileExists) {
            System.out.println("File " + newFileId + " already exists for keyword '" + searchKeyword + "'!");
            System.out.println("Skipping add operation.");

            // Send termination signal to servers
            for (int i = 0; i < serverCount; i++) {
                sendToServer(new long[] { 0 }, (i + 1));
            }
            return;
        }

        System.out.println("Search Keyword: " + searchKeyword + " | File ID to add: " + newFileId);

        updateOptValues(newFileId, true, searchKeyword);

        System.out.println("Free slot available: " + freeSlot + ".");

        updateAddrData(freeSlot);
    }

    public void deleteKeywordOpt(String searchKeyword)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        serverCount = 4;

        for (int i = 0; i < serverCount; i++) {
            sendToServer("DELETE_KEYWORD", (i + 1));
        }

        getKeywordIndex(searchKeyword);

        // to inform the servers to terminate if client has no access on the keyword or
        // does not wish to continue
        if (phase1Result == -1) {
            log.info("Not found '" + searchKeyword + "'.");

            for (int i = 0; i < serverCount; i++) {
                sendToServer(new long[][] { { 0 } }, (i + 1));
            }
            return;
        }
        // System.out.println("The index of keyword " + searchKeyword + " is " +
        // (phase1Result + 1) + ".");

        // update the act list

        StringBuilder[][] actUpdate = new StringBuilder[clientCount + 2][keywordCount];
        for (int i = 0; i < clientCount + 2; i++) {
            for (int j = 0; j < keywordCount; j++) {
                actUpdate[i][j] = new StringBuilder("0");
            }
        }
        for (int i = 0; i < clientCount; i++) {
            actUpdate[i + 2][phase1Result] = new StringBuilder(BigInteger.ZERO.toString());
        }

        // 1. Get the base numeric representation
        StringBuilder numericText = keywordToNumber(searchKeyword);

        // 2. Apply the exact same padding used during insertion
        while (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
            numericText.append(Constant.getPadding()); // "99"
        }

        // 3. Convert the FULLY PADDED string to a BigInteger
        String keywordNumber = numericText.toString();
        BigInteger kwBigInt = new BigInteger(keywordNumber);

        BigInteger modValueBig = Constant.getModBigParameter();

        // 4. Calculate the additive inverse of the padded number
        BigInteger negated = modValueBig.subtract(kwBigInt.mod(modValueBig)).mod(modValueBig);

        // 5. Send it to the ACT update matrix
        actUpdate[0][phase1Result] = new StringBuilder(negated.toString());

        System.out.println("Padded Numeric: " + kwBigInt.toString());
        System.out.println("Negated Inverse: " + negated.toString());

        // send the act list

        serverCount = 4;

        doubleDataBigString = actUpdate;

        actUpdate = null;

        doubleDSharesBigString = new StringBuilder[serverCount][clientCount + 2][1];
        for (int j = 0; j < serverCount; j++) {
            for (int i = 0; i < (clientCount + 2); i++) {
                doubleDSharesBigString[j][i][0] = new StringBuilder("");
            }
        }
        task09();

        // sending search keyword shares and client id to the server

        byte[][][] result = new byte[serverCount][clientCount + 2][];
        for (int j = 0; j < serverCount; j++) {
            // System.out.println("j:"+j);
            for (int i = 0; i < (clientCount + 2); i++) {
                result[j][i] = doubleDSharesBigString[j][i][0].toString().getBytes(StandardCharsets.UTF_8);
            }
        }

        byte[][] data2D;

        for (int i = 0; i < serverCount; i++) {
            if (i <= 2) {
                data2D = result[Math.floorMod(i - 1, 3)];
            } else {
                data2D = result[i];
            }
            sendToServer(data2D, (i + 1));
            // System.out.println("sent");
        }
    }

    public void deleteFileOpt(String searchKeyword, long fileIdToDelete)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        serverCount = 3;

        for (int i = 0; i < serverCount; i++) {
            sendToServer("DELETE_FILE", (i + 1));
        }

        // Receive dimensions
        List<Object> objects = startClient(serverCount);

        int[][] dimensions = new int[serverCount][];
        for (int i = 0; i < serverCount; i++) {
            dimensions[i] = (int[]) objects.get(i);
        }

        // Update local values
        this.optRow = dimensions[0][0];
        this.optCol = dimensions[0][1];

        serverCount = 3;
        getKeywordIndex(searchKeyword);

        // Terminate if client has no access
        if (phase1Result == -1) {
            log.info("Not found '" + searchKeyword + "'.");
            for (int i = 0; i < serverCount; i++) {
                sendToServer(new long[] { 0 }, (i + 1));
            }
            return;
        }

        getAddrData();
        getOptValues();

        // Call the decoupled method! It handles the hash rebuild and the network
        // transmission!
        updateOptValues(fileIdToDelete, false, searchKeyword);
    }

    /**
     * Generate no access code for each keyword same across all clients for a given
     * keyword
     */
    public void generateNoAccessCodes() {
        noAccessCode = new BigInteger[keywordCount];
        BigInteger bigInteger = Constant.getMaxLimitNoAccess().subtract(Constant.getMinLimitNoAccess());
        int len = Constant.getMaxLimitNoAccess().bitLength();
        BigInteger res;
        for (int i = 0; i < keywordCount; i++) {
            Random randNum = new Random((seedDBO + (i + 1)));
            // System.out.println("Seed inside batch: " + (seedDBO + (i + 1)));
            res = new BigInteger(len, randNum);
            if (res.compareTo(Constant.getMinLimitNoAccess()) < 0)
                noAccessCode[i] = res.add(Constant.getMinLimitNoAccess());
            else if (res.compareTo(bigInteger) >= 0)
                noAccessCode[i] = res.mod(bigInteger).add(Constant.getMinLimitNoAccess());
            else
                noAccessCode[i] = res;
        }
    }

    /**
     * Generate a unique no-access code for a specific keyword
     * The same code is used for all clients who don't have access to this keyword
     * 
     * @return BigInteger no-access code for the new keyword
     */
    private BigInteger generateNoAccessCodeForKeyword(int index) {
        BigInteger range = Constant.getMaxLimitNoAccess()
                .subtract(Constant.getMinLimitNoAccess());
        int len = Constant.getMaxLimitNoAccess().bitLength();

        Random randNum = new Random(seedDBO + index);
        // System.out.println("Seed in single: " + (seedDBO + index));
        BigInteger noAccessCode = new BigInteger(len, randNum);

        if (noAccessCode.compareTo(Constant.getMinLimitNoAccess()) < 0)
            noAccessCode = noAccessCode.add(Constant.getMinLimitNoAccess());
        else if (noAccessCode.compareTo(range) >= 0) // ✅ Changed to else if
            noAccessCode = noAccessCode.mod(range)
                    .add(Constant.getMinLimitNoAccess());

        return noAccessCode;
    }

    /**
     * To generate a random BigInteger value
     *
     * @param seed The seed of generating the random value
     * @return The random BigInteger value
     */
    public static BigInteger generateBigIntegerRandomNum(long seed) {
        BigInteger bigInteger = Constant.getMaxLimitNoAccess().subtract(Constant.getMinLimitNoAccess());
        Random randNum = new Random(seed);
        int len = Constant.getMaxLimitNoAccess().bitLength();
        BigInteger res;
        BigInteger temp1 = BigInteger.valueOf(0);
        res = new BigInteger(len, randNum);
        if (res.compareTo(Constant.getMinLimitNoAccess()) < 0)
            temp1 = res.add(Constant.getMinLimitNoAccess());
        if (res.compareTo(bigInteger) >= 0)
            temp1 = res.mod(bigInteger).add(Constant.getMinLimitNoAccess());

        return temp1;
    }

    /**
     * To perform cleanup tasks in course of program execution
     */
    public void cleanUpOpData() {
        perServerTime = new ArrayList<>();
        removeTime = new ArrayList<>();
        comTime = new ArrayList<>();
        waitTime = new ArrayList<>();
    }

    /**
     * create shares of updated access
     */
    private void task01() {
        IntStream.range(0, singleData.length).parallel().forEach(i -> {
            try {
                BigInteger[] shares = shamirSecretSharingAsBigInt(new StringBuilder(String.valueOf(singleData[i])),
                        serverCount);
                for (int j = 0; j < serverCount; j++) {
                    singleDShares[j][i] = shares[j];
                }
            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }

        });
    }

    private void task02() {
        IntStream.range(0, singleDataLong.length).parallel().forEach(i -> {
            try {
                long[] shares = shamirSecretSharingAsLong(singleDataLong[i], serverCount);
                for (int j = 0; j < serverCount; j++) {
                    singleDSharesLong[j][i] = shares[j];
                }
            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }
        });
    }

    private void task03() {
        int l = doubleDataLong[0].length;
        IntStream.range(0, doubleDataLong.length).parallel().forEach(k -> {
            try {
                long[] temp = doubleDataLong[k];
                for (int i = 0; i < l; i++) {
                    long[] shares = shamirSecretSharingAsLong(temp[i], serverCount);
                    for (int j = 0; j < serverCount; j++)
                        doubleDSharesLong[j][k][i] = shares[j];
                }
            } catch (Exception e) {
                System.out.println("Error occured at index " + k);
                e.printStackTrace();
            }
        });
    }

    private void task05() {
        IntStream.range(0, serverSharesAsLong_2D[0].length - 1).parallel().forEach(i -> {
            try {
                long[] shares = new long[serverCount];
                for (int j = 0; j < serverSharesAsLong_2D[0][0].length; j++) {
                    for (int l = 0; l < serverCount; l++) {
                        shares[l] = serverSharesAsLong_2D[l][i][j];
                    }
                    doubleDataLong[i][j] = lagrangeInterpolationAsLong(shares);
                }
            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }
        });
    }

    private void task06() {
        IntStream.range(phase1Result, keywordCount).parallel().forEach(i -> {
            try {
                long start_, count;

                if ((doubleDataLong[phase1Result][0] / optCol) == (doubleDataLong[i][0] / optCol)) {
                    if (i != phase1Result) {
                        start_ = doubleDataLong[i][0] + 2;
                        count = doubleDataLong[i][1];
                    } else {
                        start_ = doubleDataLong[i][0];
                        count = doubleDataLong[i][1] + 1;
                    }

                    // 🚨 WARNING: In your original code, nothing happens here!
                    // If you need to update the ADDR table for this branch too,
                    // you must call computeAndStoreAddrHashes(i, start_, count) here!

                } else {
                    start_ = doubleDataLong[i][0] + optCol;
                    if (i == phase1Result) {
                        count = doubleDataLong[i][1] + 1;
                    } else {
                        count = doubleDataLong[i][1];
                    }
                }
                computeAndStoreAddrHashes(i, start_, count);

            } catch (Exception e) {
                System.out.println("Error occurred at row index " + i);
                e.printStackTrace();
            }
        });
    }

    /**
     * Helper method to cleanly handle the heavy cryptographic hashing for a single
     * ADDR row.
     */
    private void computeAndStoreAddrHashes(int rowIndex, long startPos, long count) throws NoSuchAlgorithmException {
        // 1. Set the raw values
        addr[rowIndex][0] = startPos;
        addr[rowIndex][1] = count;

        // 2. Hash of (startPos, count)
        String hashAC = hashDigest(hashDigest(String.valueOf(startPos)) + count);
        long[] temp = getHashBlocks(hashAC);

        for (int j = 0; j < Constant.getHashBlockCount(); j++) {
            addr[rowIndex][2 + j] = temp[j];
        }

        // 3. Accumulate position hashes
        // Note: I added a reset here. If this is a reused array, you MUST clear old
        // position hashes before accumulating new ones, or they will corrupt!
        for (int j = 0; j < Constant.getHashBlockCount(); j++) {
            addr[rowIndex][2 + Constant.getHashBlockCount() + j] = 0;
        }

        for (int j = (int) startPos; j < startPos + count; j++) {
            String hash = hashDigest(String.valueOf(j));
            int pointer = 0;

            for (int k = 0; k < hash.length(); k += Constant.getHashBlockSize()) {
                int end_ = Math.min(k + Constant.getHashBlockSize(), hash.length());
                long parsedHashChunk = Long.parseLong(hash.substring(k, end_));

                int targetIndex = 2 + Constant.getHashBlockCount() + pointer;

                addr[rowIndex][targetIndex] = Helper.mod(
                        addr[rowIndex][targetIndex] + Helper.mod(parsedHashChunk));

                pointer++;
            }
        }
    }

    private void task07() {
        IntStream.range(0, doubleDataBig.length).parallel().forEach(k -> {
            try {
                for (int i = 0; i < doubleDataBig[0].length; i++) {
                    BigInteger[] shares = shamirSecretSharingAsBigInt(new StringBuilder(doubleDataBig[k][i].toString()),
                            serverCount);
                    for (int j = 0; j < serverCount; j++)
                        doubleDSharesBig[j][k][i] = shares[j];
                }
            } catch (Exception e) {
                System.out.println("Error occured at index " + k);
                e.printStackTrace();
            }
        });
    }

    private void task09() {
        IntStream.range(0, doubleDataBigString.length).parallel().forEach(k -> {
            try {
                int l = doubleDataBigString[0].length;
                StringBuilder[] temp = doubleDataBigString[k];
                for (int i = 0; i < l; i++) {
                    if (i == phase1Result) {
                        BigInteger[] shares = shamirSecretSharingAsBigInt(temp[i], serverCount);
                        for (int j = 0; j < serverCount; j++)
                            doubleDSharesBigString[j][k][0].append(shares[j]).append(",");
                    } else {
                        long[] sharesLong = shamirSecretSharingAsLong(Long.parseLong(temp[i].toString()), serverCount);
                        for (int j = 0; j < serverCount; j++)
                            doubleDSharesBigString[j][k][0].append(sharesLong[j]).append(",");
                    }
                }

            } catch (Exception e) {
                System.out.println("Error occured at index " + k);
                e.printStackTrace();
            }
        });
    }

    private void task11() {
        BigInteger value0 = BigInteger.ZERO;
        IntStream.range(0, keywordCount).parallel().forEach(i -> {
            try {
                BigInteger interpolatedValue;
                BigInteger[] shares = new BigInteger[serverCount];

                for (int j = 0; j < serverCount; j++) {
                    shares[j] = new BigInteger(objectReadString1D[j][i]);
                }
                interpolatedValue = lagrangeInterpolationAsBigInteger(shares);

                if (interpolatedValue.equals(value0)) {
                    phase1Result = i;
                }
            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }

        });

    }

    public void task12() {
        // 1. Declare constants outside the stream to save memory
        BigInteger s11 = BigInteger.valueOf(1);
        BigInteger s12 = BigInteger.valueOf(2);
        BigInteger s13 = BigInteger.valueOf(3);
        BigInteger value0 = BigInteger.ZERO;

        // 2. Stream through all keywords
        IntStream.range(0, keywordCount).parallel().forEach(i -> {
            try {
                // Read shares for this specific keyword
                BigInteger s1 = new BigInteger(objectReadString1D[0][i]);
                BigInteger s2 = new BigInteger(objectReadString1D[1][i]);
                BigInteger s3 = new BigInteger(objectReadString1D[2][i]);

                // 3. Local variables replace the old threadNum arrays!
                BigInteger val12 = Helper.mod(Helper.mod(s12.multiply(s1)).subtract(Helper.mod(s11.multiply(s2))));
                BigInteger val23 = Helper.mod(Helper.mod(s13.multiply(s2)).subtract(Helper.mod(s12.multiply(s3))));
                BigInteger val31 = Helper.mod(Helper.mod(s13.multiply(s1)).subtract(Helper.mod(s11.multiply(s3))));

                // 4. Check if any pair evaluates to 0 (Target keyword found)
                if (val12.equals(value0) || val23.equals(value0) || val31.equals(value0)) {
                    phase1Result = i;

                    // 5. VERIFICATION: Do they ALL match?
                    if (!(val12.equals(val23) && val23.equals(val31))) {
                        System.err.println(
                                "🚨 SECURITY ALERT: Servers returned inconsistent verification shares at keyword " + i);
                        // Replaces the old 'break;'. Stops processing this specific iteration.
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error occurred at keyword index " + i);
                e.printStackTrace();
            }
        });
    }

    private void task13() {
        BigInteger value0 = BigInteger.ZERO;

        IntStream.range(0, keywordCount).parallel().forEach(i -> {
            try {
                BigInteger interpolatedValue;
                BigInteger[] shares = new BigInteger[serverCount];
                for (int j = 0; j < serverCount; j++) {
                    shares[j] = new BigInteger(objectReadString1D[j][i]);
                }
                interpolatedValue = lagrangeInterpolationAsBigInteger(shares);

                if (interpolatedValue.equals(value0) || interpolatedValue.equals(noAccessCode[i])) {
                    phase1Result = i;
                    phase1ResultAccess = interpolatedValue;
                }

            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }
        });
    }

    // multithreaded across number of keywords
    private void task21() {
        IntStream.range(0, keywordCount).parallel().forEach(i -> {
            try {
                long[] shares = shamirSecretSharingAsLong(keywordVector[i], serverCount);
                for (int j = 0; j < serverCount; j++)
                    keywordVectorShares[j][i] = shares[j];
            }

            catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }
        });

    }

    private void task22() {
        IntStream.range(0, optRow).parallel().forEach(i -> {
            try {
                long[] shares = shamirSecretSharingAsLong(optInvRowVec[i], serverCount);
                for (int j = 0; j < serverCount; j++)
                    optInvRowVecShare[j][i] = shares[j];
            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }
        });
    }

    private void task31() {
        IntStream.range(0, fileCount).parallel().forEach(i -> {
            try {
                long[] shares;

                for (int m = 0; m < fileVectors.length; m++) {
                    shares = shamirSecretSharingAsLong(fileVectors[m][i], serverCount);
                    for (int j = 0; j < serverCount; j++)
                        fileVectorsShares[j][m][i] = shares[j];
                }

            } catch (Exception e) {
                System.out.println("Error occured at index " + i);
                e.printStackTrace();
            }
        });

    }

    public void task32() {
        // Loop through each retrieved file sequentially
        for (int i = 0; i < phase3Result.length; i++) {

            final int fileIndex = i; // Capture 'i' for the lambda

            IntStream.range(0, fileLength).parallel().forEach(j -> {
                try {
                    long[] shares = new long[serverCount];
                    for (int l = 0; l < serverCount; l++) {
                        shares[l] = serverSharesAsLong_2D[l][fileIndex][j];
                    }

                    // Decrypt the block and write it to its exact coordinate
                    phase3Result[fileIndex][j] = lagrangeInterpolationAsLong(shares);

                    // Extract hashes (Array writes are safe in parallel!)
                    if (j < Constant.getHashBlockCount()) {
                        hash[fileIndex][j] = phase3Result[fileIndex][j];
                    }
                } catch (Exception e) {
                    System.err.println("Error occurred decrypting file " + fileIndex + " at block " + j);
                    e.printStackTrace();
                }
            });

            StringBuilder str = new StringBuilder();

            // Start reading right AFTER the hash blocks end
            for (int j = Constant.getHashBlockCount(); j < fileLength; j++) {
                str.append(phase3Result[fileIndex][j]);
            }

            content[fileIndex][0] = str.toString();
        }
    }

    public void task33() {
        IntStream.range(0, keywordCount + 1).parallel().forEach(j -> {
            try {
                // 1. Pre-allocate the gathering buffer ONCE per thread
                long[] gatheredShares = new long[serverCount];

                for (int i = 0; i < phase2Result.size(); i++) {

                    // 2. Gather into the buffer
                    for (int l = 0; l < serverCount; l++) {
                        gatheredShares[l] = serverSharesAsLong_2D[l][i][j];
                    }

                    // 3. Interpolate
                    serverVerificationPhase3[i][j] = lagrangeInterpolationAsLong(gatheredShares);

                    // 4. Generate NEW shares (Capturing the returned array safely)
                    long[] newShares = shamirSecretSharingAsLong(serverVerificationPhase3[i][j], serverCount);

                    // 5. Distribute
                    for (int l = 0; l < serverCount; l++) {
                        verificationServer2DPhase3[l][i][j] = newShares[l];
                    }
                }

            } catch (Exception e) {
                System.out.println("Error occurred at index " + j);
                e.printStackTrace();
            }
        });
    }

    /**
     * get the access and index of keyword for that client
     *
     * @throws IOException
     */
    public void getAccessKeyword(String clientId, String searchKeyword) throws IOException, ClassNotFoundException {
        // to store the stage

        phase1Result = -1;
        phase1ResultAccess = null;
        serverCount = 3;

        // creating shares for the keyword
        BigInteger[] keywordShares = createShares(searchKeyword);
        // sending to server the shares
        // sending search keyword shares and client id to the server
        BigInteger[] data1D = new BigInteger[2];

        data1D[1] = new BigInteger(clientId);
        for (int i = 0; i < serverCount; i++) {
            // Match the share to the server's rotation
            // Server 1 has x=3 shares, needs x=3 search share
            // Server 2 has x=1 shares, needs x=1 search share
            // Server 3 has x=2 shares, needs x=2 search share
            data1D[0] = keywordShares[Math.floorMod(i - 1, 3)];
            sendToServer(data1D, (i + 1));
        }

        // waiting for servers to send the search keyword index in ACT table

        List<Object> objects = startClient(serverCount);

        // extracting search keyword indexes in ACT table

        serverCount = 3;

        objectReadString1D = new String[serverCount][];
        readObjectsAsString_1D(objects); // reading data sent by servers

        if (objectReadString1D[0][0].isEmpty()) {
            keywordCount = 0;
        } else {
            keywordCount = objectReadString1D[0].length;
        }

        task13(); // running threads to interpolate the data
        // to inform the servers to terminate if client has no access on the keyword or
        // does not wish to continue
        if (phase1Result == -1) {
            log.info("Not found '" + searchKeyword + "'.");
            for (int i = 0; i < serverCount; i++) {
                sendToServer(new byte[] { 0 }, (i + 1));
            }
        }
        // System.out.println("The index of keyword " + searchKeyword + " is " +
        // (phase1Result + 1) + ".");
    }

    /**
     * to grant or revoke access of a keyword for a given client
     *
     * @throws IOException
     */
    public String revokeGrantAccess(String clientId, String searchKeyword, Boolean access)
            throws IOException, ClassNotFoundException {

        serverCount = 4;

        for (int i = 0; i < serverCount; i++) {
            sendToServer("REVOKE_GRANT_ACCESS", (i + 1));
        }

        int cId = Integer.parseInt(clientId);
        if (cId < 0 || cId > clientCount) {
            System.out.println("ERROR: Invalid client ID: " + clientId);
            return "INVALID_CLIENT_ID";
        }

        serverCount = 3;
        // getting access and index for keyword
        getAccessKeyword(clientId, searchKeyword);
        // the vector containing updated access for the client for the given keyword

        if (phase1Result == -1)
            return "KEYWORD_NOT_FOUND";
        serverCount = 4;

        singleData = new BigInteger[keywordCount];
        for (int i = 0; i < keywordCount; i++) {
            singleData[i] = BigInteger.valueOf(0);
        }
        singleDShares = new BigInteger[serverCount][keywordCount];
        // to update the access of the keyword for that client
        boolean flag = false;

        if (access) { // access grant
            if (!phase1ResultAccess.equals(BigInteger.valueOf(0L))) { // access not present by doing -noaccesscode
                singleData[phase1Result] = Helper.mod(BigInteger.valueOf(0).subtract(noAccessCode[phase1Result]));
                flag = true;
            }
        } else { // access revoke
            if (phase1ResultAccess.equals(BigInteger.valueOf(0L))) { // access present
                singleData[phase1Result] = noAccessCode[phase1Result];
                flag = true;
            }
        }
        // send the update access list of client to server for all keywords
        if (flag) {
            task01();
            StringBuilder[] temp = new StringBuilder[serverCount];
            for (int i = 0; i < serverCount; i++) {
                temp[i] = new StringBuilder("");
            }
            // appending all keywords new access
            for (int i = 0; i < serverCount; i++) {
                for (int j = 0; j < keywordCount; j++) {
                    temp[i].append(singleDShares[i][j]).append(",");
                }
                temp[i].append(clientId);
            }
            // sending new keyword access for the client
            byte[] data1D;
            for (int i = 0; i < serverCount; i++) {
                if (i <= 2) {
                    data1D = temp[Math.floorMod(i - 1, 3)].toString().getBytes(StandardCharsets.UTF_8);
                } else {
                    data1D = temp[3].toString().getBytes(StandardCharsets.UTF_8);
                }
                sendToServer(data1D, (i + 1));
                // System.out.println("sent");
            }
            if (access) {
                System.out.println("Access granted successfully.");
                return "ACCESS_GRANTED";
            } else {
                System.out.println("Access revoked successfully.");
                return "ACCESS_REVOKED";
            }

        } else {
            System.out.println("Access required already exists!");
            // sending search keyword shares and client id to the server
            BigInteger[] data1D = new BigInteger[1];
            for (int i = 0; i < serverCount; i++) {
                data1D[0] = BigInteger.valueOf(0);
                sendToServer(data1D, (i + 1));
            }
            return "ACCESS_EXISTS";
        }
    }

    public String revokeGrantAccessBatch(String clientId, List<String> searchKeywords, Boolean access)
            throws IOException, ClassNotFoundException {

        serverCount = 4;

        for (int i = 0; i < serverCount; i++) {
            sendToServer("REVOKE_GRANT_ACCESS_BATCH", (i + 1));
        }

        for (int i = 0; i < serverCount; i++) {
            sendToServer(searchKeywords.size(), (i + 1));
        }

        int cId = Integer.parseInt(clientId);
        if (cId < 0 || cId > clientCount) {
            System.out.println("ERROR: Invalid client ID: " + clientId);
            return "INVALID_CLIENT_ID";
        }

        serverCount = 3;
        LinkedHashMap<String, Integer> keywordIndices = new LinkedHashMap<>();
        for (String searchKeyword : searchKeywords) {
            // getting access and index for keyword
            getAccessKeyword(clientId, searchKeyword);
            // the vector containing updated access for the client for the given keyword
            if (phase1Result == -1)
                System.out.println("WARNING: Keyword '" + searchKeyword + "' not found.");
            else
                keywordIndices.put(searchKeyword, phase1Result);
        }

        serverCount = 4;

        singleData = new BigInteger[keywordCount];
        for (int i = 0; i < keywordCount; i++) {
            singleData[i] = BigInteger.valueOf(0);
        }
        singleDShares = new BigInteger[serverCount][keywordCount];

        for (Map.Entry<String, Integer> entry : keywordIndices.entrySet()) {
            String searchKeyword = entry.getKey();
            int keywordIndex = entry.getValue();

            // to update the access of the keyword for that client
            boolean flag = false;

            if (access) { // access grant
                if (!phase1ResultAccess.equals(BigInteger.valueOf(0L))) { // access not present by doing -noaccesscode
                    singleData[keywordIndex] = Helper.mod(BigInteger.valueOf(0).subtract(noAccessCode[keywordIndex]));
                    flag = true;
                }
            } else { // access revoke
                if (phase1ResultAccess.equals(BigInteger.valueOf(0L))) { // access present
                    singleData[keywordIndex] = noAccessCode[keywordIndex];
                    flag = true;
                }
            }
            if (!flag) {
                System.out.println("Access required for keyword '" + searchKeyword + "' already exists!");
            }

        }

        // send the update access list of client to server for all keywords
        task01();
        StringBuilder[] temp = new StringBuilder[serverCount];
        for (int i = 0; i < serverCount; i++) {
            temp[i] = new StringBuilder("");
        }
        // appending all keywords new access
        for (int i = 0; i < serverCount; i++) {
            for (int j = 0; j < keywordCount; j++) {
                temp[i].append(singleDShares[i][j]).append(",");
            }
            temp[i].append(clientId);
        }
        // sending new keyword access for the client
        byte[] data1D;
        for (int i = 0; i < serverCount; i++) {
            if (i <= 2) {
                data1D = temp[Math.floorMod(i - 1, 3)].toString().getBytes(StandardCharsets.UTF_8);
            } else {
                data1D = temp[3].toString().getBytes(StandardCharsets.UTF_8);
            }
            sendToServer(data1D, (i + 1));
            // System.out.println("sent");
        }
        if (access) {
            return "ACCESS_GRANTED";
        } else {
            return "ACCESS_REVOKED";
        }
    }

    public LinkedHashMap<String, Integer> getAllKeywords() {
        // 1. Send the command to the servers.
        List<Object> objects = new ArrayList<>();
        serverCount = 3;
        try {
            for (int i = 0; i < serverCount; i++) {
                sendToServer("GET_KEYWORDS", (i + 1));
            }

            // 2. Wait for the servers to send the list back.
            objects = startClient(serverCount);
        } catch (Exception e) {
            e.printStackTrace();
        }

        objectReadString1D = new String[serverCount][];
        readObjectsAsString_1D(objects);

        LinkedHashMap<String, Integer> cachedKeywords = new LinkedHashMap<>();

        if (objectReadString1D[0][0].isEmpty()) {
            keywordCount = 0; // Number of keywords
            return new LinkedHashMap<>();
        } else {
            keywordCount = objectReadString1D[0].length; // Number of keywords
            long countShares[] = new long[3];
            // Interpolate each keyword
            for (int i = 0; i < keywordCount; i++) {
                BigInteger[] shares = new BigInteger[3];
                for (int j = 0; j < 3; j++) {
                    shares[j] = new BigInteger(objectReadString1D[j][i].split(":")[0]);
                    countShares[j] = Long.parseLong(objectReadString1D[j][i].split(":")[1]);
                }

                int count = (int) lagrangeInterpolationAsLong(countShares);
                BigInteger numericKeyword = lagrangeInterpolationAsBigInteger(shares);

                if (numericKeyword.equals(BigInteger.ZERO)) {
                    continue;
                }

                String keyword = decryptNumericKeyword(numericKeyword.toString());

                cachedKeywords.put(keyword, count);
            }
        }
        return cachedKeywords;

    }

    public Map<String, Set<String>> getClientAccessMap(List<String> keywords)
            throws IOException, ClassNotFoundException {
        if (clientCount == 0) {
            return new HashMap<>();
        }

        // System.out.println("\n=== BUILDING USER ACCESS MAP (BATCH OPTIMIZED) ===");
        // System.out.println("Clients: " + clientCount);
        // System.out.println("Keywords: " + keywords.size());

        serverCount = 3;

        // Request all client access data from all servers
        for (int i = 0; i < 3; i++) {
            sendToServer("GET_ALL_CLIENT_ACCESS", (i + 1));
        }

        List<Object> objects = startClient(serverCount);

        // Receive data with share labels
        BigInteger[][][] allShares = new BigInteger[4][][]; // Index by share label (1-3)
        int[] shareLabels = new int[3];

        for (int i = 0; i < 3; i++) {
            Object[] response = (Object[]) objects.get(i);
            int shareLabel = (Integer) response[0];
            BigInteger[][] clientRows = (BigInteger[][]) response[1];

            shareLabels[i] = shareLabel;
            allShares[shareLabel] = clientRows; // ✅ Store at correct share label index!

            // System.out.println("Server " + (i + 1) + " has share label " + shareLabel +
            // ", " + clientRows.length + " clients");
        }

        // Verify we have shares 1, 2, 3
        if (allShares[1] == null || allShares[2] == null || allShares[3] == null) {
            return new HashMap<>();
        }

        // Build keyword index map
        Map<String, Integer> keywordIndexMap = new HashMap<>();
        for (int i = 0; i < keywords.size(); i++) {
            keywordIndexMap.put(keywords.get(i), i);
        }

        Map<String, Set<String>> clientAccessMap = new HashMap<>();

        // Process all clients locally
        for (int clientId = 1; clientId <= clientCount; clientId++) {
            Set<String> allowedKeywords = new HashSet<>();
            int clientIndex = clientId - 1;

            // Interpolate this client's access codes
            BigInteger[] clientAccessCodes = new BigInteger[allShares[1][clientIndex].length];

            for (int k = 0; k < clientAccessCodes.length; k++) {
                // ✅ CORRECT: Use shares in label order (1, 2, 3)
                BigInteger[] keywordShares = new BigInteger[3];
                keywordShares[0] = allShares[1][clientIndex][k]; // Share with label 1
                keywordShares[1] = allShares[2][clientIndex][k]; // Share with label 2
                keywordShares[2] = allShares[3][clientIndex][k]; // Share with label 3

                clientAccessCodes[k] = lagrangeInterpolationAsBigInteger(keywordShares);
            }

            // Count zeros for debugging
            int zeroCount = 0;
            for (BigInteger code : clientAccessCodes) {
                if (code.equals(BigInteger.ZERO)) {
                    zeroCount++;
                }
            }

            // Check each keyword
            int allowedCount = 0;
            for (String keyword : keywords) {
                Integer keywordIndex = keywordIndexMap.get(keyword);

                if (keywordIndex != null && keywordIndex < clientAccessCodes.length) {
                    if (clientAccessCodes[keywordIndex].equals(BigInteger.ZERO)) {
                        allowedKeywords.add(keyword);
                        allowedCount++;
                    }
                }
            }

            clientAccessMap.put(String.valueOf(clientId), allowedKeywords);
        }

        return clientAccessMap;
    }

    private int getKeywordCountFromServers() throws IOException, ClassNotFoundException {

        serverCount = 3;

        // Send command to all servers
        for (int i = 0; i < serverCount; i++) {
            sendToServer("GET_KEYWORD_COUNT", (i + 1));
        }

        // Wait for responses
        List<Object> objects = startClient(serverCount);

        // Get counts from all servers
        int[] counts = new int[serverCount];
        for (int i = 0; i < objects.size(); i++) {
            counts[i] = (int) objects.get(i);
        }

        // Verify all servers agree
        if (counts[0] == counts[1] && counts[1] == counts[2]) {
            return counts[0];
        } else {
            throw new IllegalStateException("Server keyword count mismatch");
        }
    }

    public Map<String, Integer> getStats() {

        serverCount = 3;

        List<Object> objects = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                sendToServer("GET_STATS", (i + 1));
            }
            objects = startClient(3);
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Map<String, Integer>> stats = new ArrayList<>(3);

        for (int i = 0; i < 3; i++) {
            stats.add((Map<String, Integer>) objects.get(i)); // Initializes each element with a new HashMap
        }

        String statNames[] = { "keywords", "clients", "documents" };

        keywordCount = stats.get(0).get("keywords");
        clientCount = stats.get(0).get("clients");

        return stats.get(0);
    }

    /**
     * Decrypt numeric keyword back to text
     */
    private String decryptNumericKeyword(String numericString) {
        StringBuilder keyword = new StringBuilder();

        // Convert back to letters (reverse of keywordToNumber)
        for (int i = 0; i < numericString.length(); i += 2) {
            if (i + 2 <= numericString.length()) {
                String pair = numericString.substring(i, i + 2);

                // Stop at padding (99 is the padding value)
                if (pair.equals("99")) {
                    break; // ← Stop when we hit padding!
                }

                int asciiValue = Integer.parseInt(pair) + 86;
                keyword.append((char) asciiValue);
            }
        }

        return keyword.toString();
    }

    public int getFileCount() throws IOException, ClassNotFoundException {
        sendToServer("GET_FILE_COUNT", 1);
        List<Object> objects = startClient(1);
        fileCount = (int) objects.get(0);
        return fileCount;
    }

    /**
     * Add a new keyword to the system
     * 
     * @param keyword The keyword to add (should already be stemmed)
     */
    public String addNewKeyword(String keyword)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        serverCount = 4; // Need 4 for ACT updates

        // Send command to servers
        for (int i = 0; i < serverCount; i++) {
            sendToServer("ADD_KEYWORD", (i + 1));
        }

        // Step 1: Check if keyword already exists
        serverCount = 3; // Back to 3 for search

        if (keywordCount > 0) {
            getKeywordIndex(keyword);

            if (phase1Result != -1) {

                // Send cancel signal to servers
                serverCount = 4;
                for (int i = 0; i < serverCount; i++) {
                    sendToServer(new byte[] { 0 }, (i + 1));
                }

                return "KEYWORD_EXISTS";
            }

        }

        // Step 2: Encode keyword to numeric form
        keywordToNumber(keyword);
        if (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
            while (numericText.length() < 2 * Constant.getMaxKeywordLength())
                numericText.append(Constant.getPadding());
        }

        // System.out.println("Keyword encoded: " + numericText);

        // ===== STEP 3: CREATE ACT SHARES =====
        serverCount = 4; // Create 4 shares to match DataOutsource

        // Step 3a: Create shares for keyword numeric value
        BigInteger[] keywordShares = shamirSecretSharingAsBigInt(numericText, serverCount);

        // Step 3b: Create shares for position (new position = keywordCount)
        long newPosition = keywordCount;
        long[] positionShares = shamirSecretSharingAsLong(newPosition, serverCount);

        // Step 3c: Create access shares for all clients
        BigInteger[][] accessShares = new BigInteger[clientCount][serverCount];
        BigInteger accessValue = generateNoAccessCodeForKeyword(keywordCount + 1);

        for (int i = 0; i < clientCount; i++) {
            accessShares[i] = shamirSecretSharingAsBigInt(
                    new StringBuilder(accessValue.toString()), serverCount);
        }

        // Step 3d: Format and send ACT data to each server

        for (int i = 0; i < serverCount; i++) {
            StringBuilder actUpdate = new StringBuilder();

            // Determine which share index to use based on server's expected rotation
            int shareIndex;
            if (i == 0) {
                shareIndex = 2; // Server 1 expects x=3 share (index 2)
            } else if (i == 1) {
                shareIndex = 0; // Server 2 expects x=1 share (index 0)
            } else if (i == 2) {
                shareIndex = 1; // Server 3 expects x=2 share (index 1)
            } else {
                shareIndex = 3; // Server 4 expects x=4 share (index 3)
            }

            // Row 1: Keyword numeric (with rotation)
            actUpdate.append(keywordShares[shareIndex]).append(",");

            // Row 2: Position (with rotation)
            actUpdate.append(positionShares[shareIndex]).append(",");

            // Rows 3+: Access for each client (with rotation)
            for (int j = 0; j < clientCount; j++) {
                actUpdate.append(accessShares[j][shareIndex]).append(",");
            }

            byte[] data = actUpdate.toString().getBytes(StandardCharsets.UTF_8);
            sendToServer(data, (i + 1));
        }

        // ===== STEP 4: RECEIVE CURRENT OPT SIZE FROM SERVERS =====

        List<Object> objects = startClient(3);

        int receivedOptRow = -1;
        long[][] lastAddrShares = new long[3][2 + 2 * Constant.getHashBlockCount()];

        for (int i = 0; i < objects.size(); i++) {
            long[] data = (long[]) objects.get(i);
            int serverLabel = (int) data[data.length - 1];

            receivedOptRow = (int) data[0];
            System.arraycopy(data, 1, lastAddrShares[serverLabel - 1], 0, data.length - 2);
        }

        optRow = receivedOptRow;
        System.out.println("✓ optRow = " + optRow);

        // Interpolate last ADDR
        long[] shares = new long[3];
        shares[0] = lastAddrShares[0][0];
        shares[1] = lastAddrShares[1][0];
        shares[2] = lastAddrShares[2][0];
        long lastStartPos = lagrangeInterpolationAsLong(shares);

        shares[0] = lastAddrShares[0][1];
        shares[1] = lastAddrShares[1][1];
        shares[2] = lastAddrShares[2][1];

        int keywordSpacing = 50;

        int newStartPos = (int) (lastStartPos + keywordSpacing);

        optRow = receivedOptRow;

        // ===== STEP 5: CREATE AND SEND ADDR DATA =====

        // Step 5a: Allocate position in inverted index

        // Step 5b: Create ADDR entry
        long[] addrEntry = new long[2 + 2 * Constant.getHashBlockCount()];
        addrEntry[0] = newStartPos;
        addrEntry[1] = Constant.getHashBlockCount();

        // Compute hash of startPos and count
        String hashAC = hashDigest(hashDigest(String.valueOf(newStartPos)) + addrEntry[1]);
        long[] hashBlocks = getHashBlocks(hashAC);
        for (int i = 0; i < Constant.getHashBlockCount(); i++) {
            addrEntry[2 + i] = hashBlocks[i];
        }

        // Hash of positions (initially empty)
        for (int i = 0; i < Constant.getHashBlockCount(); i++) {
            addrEntry[2 + Constant.getHashBlockCount() + i] = 0;
        }

        System.out.println("ADDR entry created: startPos=" + newStartPos + ", count=" + addrEntry[1]);

        // Step 5c: Create shares (only 3 shares for ADDR)
        serverCount = 3;
        long[][][] addrShares = new long[serverCount][1][addrEntry.length];
        for (int i = 0; i < addrEntry.length; i++) {
            shares = shamirSecretSharingAsLong(addrEntry[i], serverCount);
            for (int j = 0; j < serverCount; j++) {
                addrShares[j][0][i] = shares[j];
            }
        }

        // Step 5d: Send to servers with rotation
        for (int i = 0; i < serverCount; i++) {
            sendToServer(addrShares[Math.floorMod(i - 1, 3)][0], (i + 1));
        }

        // ===== STEP 6: UPDATE CLIENT STATE =====

        // Increment keyword count
        keywordCount++;

        // Expand noAccessCode array
        BigInteger[] newNoAccessCode = new BigInteger[keywordCount];
        System.arraycopy(noAccessCode, 0, newNoAccessCode, 0, keywordCount - 1);
        newNoAccessCode[keywordCount - 1] = accessValue;
        noAccessCode = newNoAccessCode;

        // Increment optRow (we allocated a new row)
        /// optRow++;

        return "SUCCESS";
    }

    // ============================================================================
    // OPTIMIZED BULK UPLOAD
    // ============================================================================
    public String bulkUploadFiles(List<FileUpload> files, Set<String> actKeywords)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        long totalStartTime = System.currentTimeMillis();

        // ========================================================================
        // PHASE 1: KEYWORD EXTRACTION & BATCH ADDITION
        // ========================================================================
        long phase1Start = System.currentTimeMillis();

        // Step 1.1: Extract ALL unique keywords from ALL files
        System.out.println("Extracting keywords from " + files.size() + " files...");

        Set<String> allUniqueKeywords = new LinkedHashSet<>();
        List<Document> docsList = new ArrayList<>();
        int totalKeywordOccurrences = 0;
        int currentFileCount = getFileCount();

        for (FileUpload file : files) {
            if (file.keywords == null || file.keywords.length == 0) {
                continue; // Skip files with no keywords
            }
            docsList.add(new Document(file.getFilename(), LocalDateTime.now()));
            allUniqueKeywords.addAll(Arrays.asList(file.keywords));
            totalKeywordOccurrences += file.keywords.length;
        }

        documentRepository.saveAll(docsList);

        // Step 1.2: Check which keywords already exist
        Set<String> newKeywords = new LinkedHashSet<>();

        // Fetching keyword indices to check if they are present in the ACT

        Map<String, Integer> keywordIndexMap = new HashMap<>();
        serverCount = 3;
        for (String keyword : allUniqueKeywords) {
            for (int i = 0; i < serverCount; i++) {
                sendToServer("GET_KEYWORD_INDEX", (i + 1));
            }
            getKeywordIndex(keyword);

            if (phase1Result == -1) {
                newKeywords.add(keyword);
            } else {
                keywordIndexMap.put(keyword, phase1Result);
            }
        }

        // Step 1.3: Batch add ALL new keywords in ONE operation
        if (!newKeywords.isEmpty()) {
            long batchStart = System.currentTimeMillis();

            String result = addNewKeywordsBatch(newKeywords.toArray(new String[0]));

            if (!"SUCCESS".equals(result)) {
                return "KEYWORD_ADD_FAILED: " + result;
            }

        }

        // Step 1.4: Build keyword index cache for NEW keywords

        for (String keyword : newKeywords) {
            for (int i = 0; i < serverCount; i++) {
                sendToServer("GET_KEYWORD_INDEX", (i + 1));
            }
            getKeywordIndex(keyword);

            if (phase1Result == -1) {
                return "KEYWORD_INDEX_ERROR: " + keyword + " not found after addition";
            }

            keywordIndexMap.put(keyword, phase1Result);
        }

        long phase1Duration = System.currentTimeMillis() - phase1Start;
        System.out.println("\n✓ Phase 1 complete: " + (phase1Duration / 1000.0) + "s");

        // ========================================================================
        // PHASE 2: BATCH FILE UPLOAD (Content + File to Keyword Lists)
        // ========================================================================
        System.out.println("\n[PHASE 2] BATCH FILE UPLOAD");
        System.out.println("-".repeat(80));
        long phase2Start = System.currentTimeMillis();

        int BATCH_SIZE = 5000;
        int totalBatches = (int) Math.ceil((double) files.size() / BATCH_SIZE);

        int successCount = 0;
        List<String> errors = new ArrayList<>();

        // Track which files need to be added to which keywords (for Phase 3)
        Map<String, Set<Integer>> keywordToFilesMap = new HashMap<>();
        for (String keyword : allUniqueKeywords) {
            keywordToFilesMap.put(keyword, new HashSet<>());
        }

        // Fetch all files from servers to add index old files with new keywords

        for (int s = 0; s < serverCount; s++) {
            sendToServer("GET_ALL_FILES", (s + 1));
        }

        List<Object> allFilesShares = startClient(3);

        long[][][] filesDataStructure = new long[3][][];

        for (Object share : allFilesShares) {
            FilesDataStructureShares filesDataStructureShares = (FilesDataStructureShares) share;
            int shareIndex = filesDataStructureShares.getServerNumber();
            filesDataStructure[shareIndex] = filesDataStructureShares
                    .getFilesShare();
        }

        String[] fileContent = new String[filesDataStructure[0].length];

        // Interpolating the file content shares to get the actual file content
        for (int i = 0; i < filesDataStructure[0].length; i++) {
            fileContent[i] = extractFileContent(
                    new long[][] { filesDataStructure[0][i], filesDataStructure[1][i], filesDataStructure[2][i] });
        }

        Map<Integer, Set<String>> fileToKeywordsMap = new HashMap<>();

        for (int i = 0; i < fileContent.length; i++) {
            Set<String> keywords = ExtractKeywordsUtil.extractKeywordsFromContent(fileContent[i]);
            fileToKeywordsMap.put(i + 1, keywords);
        }

        // ✅ Process files in batches
        for (int batch = 0; batch < totalBatches; batch++) {
            int startIdx = batch * BATCH_SIZE;
            int endIdx = Math.min(startIdx + BATCH_SIZE, files.size());
            int batchSize = endIdx - startIdx;

            System.out.println("\n[Batch " + (batch + 1) + "/" + totalBatches + "] Processing files " +
                    startIdx + "-" + (endIdx - 1) + "...");

            try {
                // ✅ Build batch data for current batch
                long[][][] batchContentShares = new long[3][batchSize][];
                long[][][] batchKeywordShares = new long[3][batchSize][];

                System.out.println("Current file count: " + currentFileCount);

                for (int i = startIdx; i < endIdx; i++) {
                    int batchIdx = i - startIdx;
                    FileUpload file = files.get(i);
                    int newFileId = currentFileCount + i + 1;

                    // Get keyword indices from cache
                    List<Integer> keywordIndices = new ArrayList<>();
                    for (String keyword : file.keywords) {

                        for (int fileId : fileToKeywordsMap.keySet()) {
                            if (fileToKeywordsMap.get(fileId).contains(keyword)) {
                                keywordToFilesMap.get(keyword).add(fileId);
                            }
                        }

                        Integer index = keywordIndexMap.get(keyword);
                        if (index == null) {
                            throw new IllegalStateException("Keyword not in cache: " + keyword);
                        }
                        keywordIndices.add(index);

                        // Track for Phase 3
                        keywordToFilesMap.get(keyword).add(newFileId);
                    }

                    // Encrypt file content
                    long[] encryptedContent = encryptFile(file.content);

                    // Create keyword list with padding
                    int pad;
                    if (file.keywords.length <= checkpoint2) {
                        pad = checkpoint2 - file.keywords.length;
                    } else {
                        pad = checkpoint1 - file.keywords.length;
                    }

                    int keywordListLength = Constant.getHashBlockCount() + file.keywords.length + pad;
                    long[] keywordIndicesList = new long[keywordListLength];

                    // Add hash blocks
                    String fileHashDigest = hashDigest(file.content);
                    long[] fileHashBlocks = getHashBlocks(fileHashDigest);
                    System.arraycopy(fileHashBlocks, 0, keywordIndicesList, 0, Constant.getHashBlockCount());

                    // Add keyword indices (1-indexed)
                    for (int j = 0; j < file.keywords.length; j++) {
                        keywordIndicesList[Constant.getHashBlockCount() + j] = keywordIndices.get(j) + 1;
                    }

                    // ✅ Create shares for this file
                    for (int j = 0; j < encryptedContent.length; j++) {
                        long[] shares = shamirSecretSharingAsLong(encryptedContent[j], 3);
                        for (int s = 0; s < 3; s++) {
                            if (batchContentShares[s][batchIdx] == null) {
                                batchContentShares[s][batchIdx] = new long[encryptedContent.length];
                            }
                            batchContentShares[s][batchIdx][j] = shares[s];
                        }
                    }

                    // Create keyword list shares
                    for (int j = 0; j < keywordIndicesList.length; j++) {
                        long[] shares = shamirSecretSharingAsLong(keywordIndicesList[j], 3);
                        for (int s = 0; s < 3; s++) {
                            if (batchKeywordShares[s][batchIdx] == null) {
                                batchKeywordShares[s][batchIdx] = new long[keywordIndicesList.length];
                            }
                            batchKeywordShares[s][batchIdx][j] = shares[s];
                        }
                    }

                    successCount++;
                }

                System.out.println("  ✓ Shares created for " + batchSize + " files");

                // ✅ Send batch to all servers (COMBINED MESSAGE)
                serverCount = 3;
                for (int s = 0; s < 3; s++) {
                    sendToServer("BULK_UPLOAD_FILES", (s + 1));

                    int shareIndex;
                    if (s == 0)
                        shareIndex = 2;
                    else if (s == 1)
                        shareIndex = 0;
                    else
                        shareIndex = 1;

                    // ✅ COMBINE content and keywords in single send
                    Object[] batchData = new Object[2];
                    batchData[0] = batchContentShares[shareIndex]; // long[][]
                    batchData[1] = batchKeywordShares[shareIndex]; // long[][]

                    sendToServer(batchData, (s + 1));
                }

                System.out.println("  ✓ Batch sent to all servers");

                // Clear batch from memory
                batchContentShares = null;
                batchKeywordShares = null;

                if (batch % 5 == 0) {
                    System.gc(); // Periodic garbage collection
                }

            } catch (Exception e) {
                String errorMsg = "Batch " + (batch + 1) + " failed: " + e.getMessage();
                errors.add(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                e.printStackTrace();
            }
        }

        long phase2Duration = System.currentTimeMillis() - phase2Start;
        System.out.println("\n✓ Phase 2 complete: " + (phase2Duration / 1000.0) + "s");
        System.out.println("✓ Success: " + successCount + "/" + files.size());
        if (successCount > 0) {
            System.out.println("✓ Avg: " + (phase2Duration / successCount) + "ms per file");
        }

        // ========================================================================
        // PHASE 3: BATCH UPDATE INVERTED INDICES
        // ========================================================================
        System.out.println("\n[PHASE 3] BATCH INVERTED INDEX UPDATE");
        System.out.println("-".repeat(80));
        long phase3Start = System.currentTimeMillis();

        System.out.println("Updating inverted indices for " + keywordToFilesMap.size() + " keywords...");

        for (Map.Entry<String, Set<Integer>> entry : keywordToFilesMap.entrySet()) {
            String keyword = entry.getKey();
            Set<Integer> fileIds = entry.getValue();
            if (fileIds.isEmpty()) {
                continue;
            }

            try {
                // ✅ OPTIMIZATION: Use Batch Add instead of loop
                System.out.println("Adding " + fileIds.size() + " files to keyword '" + keyword + "'...");
                addFilesBatchOpt(keyword, fileIds);

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("ERROR updating keyword '" + keyword + "': " + e.getMessage());
            }
        }

        long phase3Duration = System.currentTimeMillis() - phase3Start;
        System.out.println("\n✓ Phase 3 complete: " + (phase3Duration / 1000.0) + "s");

        // ========================================================================
        // FINAL SUMMARY
        // ========================================================================
        long totalDuration = System.currentTimeMillis() - totalStartTime;

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BULK UPLOAD COMPLETE");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Total time: " + (totalDuration / 1000.0) + "s (" + (totalDuration / 60000.0) + " min)");
        System.out.println();
        System.out.println("Phase 1 (Keywords):        " + (phase1Duration / 1000.0) + "s");
        System.out.println("Phase 2 (Files):           " + (phase2Duration / 1000.0) + "s");
        System.out.println("Phase 3 (Indices):         " + (phase3Duration / 1000.0) + "s");
        System.out.println();
        System.out.println("Keywords:");
        System.out.println("  - Unique:                " + allUniqueKeywords.size());
        System.out.println("  - New:                   " + newKeywords.size());
        System.out.println("  - Existing:              " + (allUniqueKeywords.size() - newKeywords.size()));
        System.out.println();
        System.out.println("Files:");
        System.out.println("  - Total:                 " + files.size());
        System.out.println("  - Success:               " + successCount);
        System.out.println("  - Failed:                " + (files.size() - successCount));
        if (successCount > 0) {
            System.out.println("  - Avg per file:          " + (phase2Duration / successCount) + "ms");
        }
        System.out.println();

        if (!errors.isEmpty() && errors.size() <= 10) {
            System.out.println("Errors:");
            for (String error : errors) {
                System.out.println("  - " + error);
            }
        } else if (!errors.isEmpty()) {
            System.out.println("Errors: " + errors.size() + " (showing first 10)");
            for (int i = 0; i < Math.min(10, errors.size()); i++) {
                System.out.println("  - " + errors.get(i));
            }
        }

        System.out.println("=".repeat(80));

        fileCount = currentFileCount + successCount;

        return "SUCCESS";
    }

    /**
     * Optimized method to add multiple files to a single keyword in one go.
     */
    public void addFilesBatchOpt(String searchKeyword, Set<Integer> fileIdsToAdd)
            throws IOException, NoSuchAlgorithmException, ClassNotFoundException {

        serverCount = 3;

        // 1. Prepare Servers (Same as before)
        for (int i = 0; i < serverCount; i++) {
            sendToServer("ADD_FILE_TO_KEYWORD", (i + 1));
        }

        // 2. Receive dimensions
        List<Object> objects = startClient(serverCount);
        int[][] dimensions = new int[serverCount][];
        for (int i = 0; i < serverCount; i++) {
            dimensions[i] = (int[]) objects.get(i);
        }
        this.optRow = dimensions[0][0];
        this.optCol = dimensions[0][1];

        // 3. Get Keyword Index
        getKeywordIndex(searchKeyword);
        if (phase1Result == -1) {
            log.info("Not found '" + searchKeyword + "'.");
            for (int i = 0; i < serverCount; i++)
                sendToServer(new long[] { 0 }, (i + 1));
            return;
        }

        // 4. Fetch Existing Data (Addr & Opt)
        getAddrData();
        getOptValues();

        // 5. Filter Duplicates locally
        List<Integer> uniqueFiles = new ArrayList<>();
        // Create a set of existing IDs from the inverted index for O(1) lookup
        Set<Long> existingIds = new HashSet<>();
        // File IDs start after the hash blocks
        for (int i = Constant.getHashBlockCount(); i < countItems; i++) {
            existingIds.add(phase2Interpolation[i]);
        }

        for (Integer id : fileIdsToAdd) {
            if (!existingIds.contains((long) id)) {
                uniqueFiles.add(id);
            }
        }

        if (uniqueFiles.isEmpty()) {
            System.out.println("All files already exist for keyword '" + searchKeyword + "'. Skipping.");
            for (int i = 0; i < serverCount; i++)
                sendToServer(new long[] { 0 }, (i + 1));
            return;
        }

        System.out.println("Adding " + uniqueFiles.size() + " unique files to '" + searchKeyword + "'");

        // 6. Perform Batch Updates
        updateOptValuesBatch(uniqueFiles);
        updateAddrDataBatch(uniqueFiles.size(), freeSlot);
    }

    public void updateOptValuesBatch(List<Integer> newFileIds) throws IOException, NoSuchAlgorithmException {

        if (countItems + newFileIds.size() < optCol) {
            freeSlot = true;
            // 1. Reconstruct current hash from the first few blocks of phase2Interpolation
            StringBuilder hDigestS = new StringBuilder("");
            StringBuilder temp;

            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                temp = new StringBuilder(String.valueOf(phase2Interpolation[i]));
                while (temp.length() < Constant.getHashBlockSize()) {
                    temp.insert(0, "0");
                }
                hDigestS.append(temp);
            }

            // 2. Calculate NEW Hash by appending ALL new file IDs in order
            String currentHashString = hDigestS.toString();
            for (Integer fid : newFileIds) {
                currentHashString = hashDigest(currentHashString + fid);
            }

            long[] newHashBlocks = getHashBlocks(currentHashString);

            // 3. Prepare Sparse Data
            List<Integer> positions = new ArrayList<>();
            List<Long> values = new ArrayList<>();

            // A. Add Hash Block Deltas (Indices 0 to 7)
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                int pos = startingPos + i;
                // Calculate delta: NewHash - OldHash
                long delta = Helper.mod(newHashBlocks[i] - phase2Interpolation[i]);

                if (delta != 0) {
                    positions.add(pos);
                    values.add(delta);
                }
            }

            // B. Add New File IDs (Indices countItems to countItems + N)
            for (int i = 0; i < newFileIds.size(); i++) {
                int filePos = startingPos + countItems + i;
                long val = newFileIds.get(i);

                positions.add(filePos);
                values.add(val);
            }

            // 4. Create and Send Shares
            int nonZeroCount = positions.size();
            long[][] sparseShares = new long[serverCount][nonZeroCount];

            for (int i = 0; i < nonZeroCount; i++) {
                long[] shares = shamirSecretSharingAsLong(values.get(i), serverCount);
                for (int s = 0; s < serverCount; s++) {
                    sparseShares[s][i] = shares[s];
                }
            }

            int[] positionsArray = positions.stream().mapToInt(Integer::intValue).toArray();

            for (int s = 0; s < serverCount; s++) {
                SparseDelta sparseDelta = new SparseDelta(positionsArray, sparseShares[s], s + 1);
                sendToServer(sparseDelta, (s + 1));
            }
        }

        else {
            System.out.println("Free Slots not available. Performing Split & Insert...");

            freeSlot = false;

            // 1. Reconstruct current hash from the first few blocks of phase2Interpolation
            StringBuilder hDigestS = new StringBuilder("");
            StringBuilder temp;

            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                temp = new StringBuilder(String.valueOf(phase2Interpolation[i]));
                while (temp.length() < Constant.getHashBlockSize()) {
                    temp.insert(0, "0");
                }
                hDigestS.append(temp);
            }

            // 2. Calculate NEW Hash by appending ALL new file IDs in order
            String currentHashString = hDigestS.toString();
            for (Integer fid : newFileIds) {
                currentHashString = hashDigest(currentHashString + fid);
            }

            long[] newHashBlocks = getHashBlocks(currentHashString);

            // 1. Combine Old Items + New Items
            List<Long> allItems = new ArrayList<>();
            // A. Add UPDATED Hash Blocks
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                allItems.add(newHashBlocks[i]);
            }

            // B. Add Existing Items
            for (int i = Constant.getHashBlockCount(); i < countItems; i++) {
                allItems.add(phase2Interpolation[i]);
            }

            // C. Add New File IDs
            for (Integer fid : newFileIds) {
                allItems.add((long) fid);
            }

            // 2. Split into Row A (Replacement) and Row B (Overflow)
            List<Long> rowA = new ArrayList<>();
            List<Long> rowB = new ArrayList<>();

            for (int i = 0; i < allItems.size(); i++) {
                if (i < optCol) {
                    rowA.add(allItems.get(i));
                } else {
                    rowB.add(allItems.get(i));
                }
            }

            // Pad Row B with zeros to match optCol size
            while (rowB.size() < optCol) {
                rowB.add(0L);
            }

            // 3. Generate Shares for both rows (Server-specific)
            // We need to send specific shares to specific servers.
            // We cannot send ONE object to all. We loop through servers.

            for (int s = 0; s < serverCount; s++) {
                long[] sharesForReplace = new long[optCol];
                long[] sharesForInsert = new long[optCol];

                // Create shares for Row A
                for (int i = 0; i < optCol; i++) {
                    long val = (i < rowA.size()) ? rowA.get(i) : 0;
                    long[] sShares = shamirSecretSharingAsLong(val, serverCount);
                    sharesForReplace[i] = sShares[s];
                }

                // Create shares for Row B
                for (int i = 0; i < optCol; i++) {
                    long val = rowB.get(i);
                    long[] sShares = shamirSecretSharingAsLong(val, serverCount);
                    sharesForInsert[i] = sShares[s];
                }

                // 4. Send Request
                int insertIdx = startingPos + optCol;

                ResizeOptRequest req = new ResizeOptRequest(
                        startingPos, // replaceIndex
                        sharesForReplace, // replaceData
                        insertIdx, // insertIndex
                        sharesForInsert // insertData
                );

                sendToServer(req, (s + 1));
            }
        }

    }

    private void updateAddrDataBatch(int amountAdded, boolean freeSlot)
            throws NoSuchAlgorithmException, IOException, ClassNotFoundException {

        if (freeSlot) {

            System.out.println("Free slot available. Updating ADDR data with " + amountAdded + " new files...");
            // 1. Update Count locally
            // We need to construct the full ADDR row update
            // We primarily need to update the COUNT column and the HASH columns.

            // New total count
            int newTotalCount = countItems + amountAdded;

            doubleDataLong = new long[keywordCount][2 + 2 * Constant.getHashBlockCount()];

            // Update the count column (Index 1)
            // Note: The delta is just 'amountAdded', but since we reconstruct the whole row
            // logic below usually:
            doubleDataLong[phase1Result][1] = amountAdded;

            // 2. Calculate New Hash for ADDR verification
            // Hash( Hash(StartPos) + NewCount )
            String hashAC = hashDigest(hashDigest(String.valueOf(startingPos)) + newTotalCount);
            long[] newHashBlocks = getHashBlocks(hashAC);

            // Calculate deltas for Hash columns (Indices 2 to 9)
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                // newHash - oldHash (stored in indexHashList)
                doubleDataLong[phase1Result][2 + i] = Helper.mod(newHashBlocks[i] - indexHashList[i]);
            }

            // 3. Update Position Vector Hash (Indices 10 to 17)
            // Logic: Hash of (StartPos + NewCount)
            String newPositionHash = hashDigest(String.valueOf((startingPos + countItems + newTotalCount)));

            long[] newPosHashBlocks = getHashBlocks(newPositionHash);

            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                doubleDataLong[phase1Result][2 + Constant.getHashBlockCount() + i] = Helper.mod(newPosHashBlocks[i] -
                        indexHashList[Constant.getHashBlockCount() + i]);
            }

            // 4. Send
            doubleDSharesLong = new long[serverCount][keywordCount][2 + 2 * Constant.getHashBlockCount()];
            task03(); // Generates shares from doubleDataLong

            for (int i = 0; i < serverCount; i++) {
                long[][] data2D = doubleDSharesLong[Math.floorMod(i - 1, 3)];
                sendToServer(data2D, (i + 1));
            }
        }

        else {
            List<Object> objects = startClient(serverCount);

            serverSharesAsLong_2D = new long[serverCount][][];
            readObjectsAsLong_2D(objects);

            long[][] addr = new long[keywordCount][2 + 2 * Constant.getHashBlockCount()];
            task05(); // Reconstructs doubleDataLong from serverSharesAs

        }

    }

    // ============================================================================
    // BULK SYSTEM INITIALIZATION - ONE-SHOT UPLOAD
    // Build everything locally, send once to servers
    // ============================================================================

    /**
     * Complete system initialization in ONE operation
     * Builds all data structures locally and sends to servers
     * 
     * For 100K files with 5000 keywords: ONE round trip!
     */
    public String bulkInitializeSystem(List<FileUpload> files)
            throws IOException, NoSuchAlgorithmException {

        System.out.println("BULK SYSTEM INITIALIZATION");
        System.out.println("Files: " + files.size());

        long startTime = System.currentTimeMillis();

        // ========================================================================
        // PHASE 1: EXTRACT AND MAP DATA
        // ========================================================================
        System.out.println("\n[PHASE 1] Extracting and mapping data...");

        Map<Integer, String> fileIdToNameMap = new HashMap<>();

        // Extract all unique keywords
        Set<String> uniqueKeywords = new LinkedHashSet<>();
        int fileIdToSave = 0;
        for (FileUpload file : files) {
            if (file.keywords == null || file.keywords.length == 0) {
                continue; // Skip files with no keywords
            }
            fileIdToNameMap.put(fileIdToSave, file.getFilename());
            fileIdToSave++;
            uniqueKeywords.addAll(Arrays.asList(file.keywords));
        }

        documentRepository.saveAll(
                fileIdToNameMap.entrySet().stream()
                        .map(e -> new Document(e.getValue(), LocalDateTime.now()))
                        .collect(Collectors.toList()));

        // Create keyword -> index mapping
        Map<String, Integer> keywordIndexMap = new HashMap<>();
        int idx = 0;
        for (String keyword : uniqueKeywords) {
            keywordIndexMap.put(keyword, idx++);
        }

        // Create keyword -> fileIDs mapping
        Map<String, List<Integer>> keywordToFiles = new HashMap<>();
        for (String keyword : uniqueKeywords) {
            keywordToFiles.put(keyword, new ArrayList<>());
        }

        for (int i = 0; i < files.size(); i++) {
            int fileId = i + 1;
            for (String keyword : files.get(i).keywords) {
                keywordToFiles.get(keyword).add(fileId);
            }
        }

        System.out.println("✓ Unique keywords: " + uniqueKeywords.size());
        System.out.println("✓ Files: " + files.size());

        int numKeywords = uniqueKeywords.size();
        int SLOTS_PER_KEYWORD = Constant.getHashBlockCount() + 10000; // 50008
        int colSize = SLOTS_PER_KEYWORD; // Each keyword gets one row of 50008 columns

        // ========================================================================
        // INITIAL SIGNAL & METADATA
        // ========================================================================
        // Notify all 4 servers that a bulk initialization is starting
        for (int s = 1; s <= 4; s++) {
            sendToServer("BULK_INITIALIZE", s);
            int[] metadata = { files.size(), colSize };
            sendToServer(metadata, s);
        }

        // ========================================================================
        // PHASE 2: BUILD ACT TABLE
        // ========================================================================
        System.out.println("\n[PHASE 2] Building ACT table...");

        int numRows = 2 + clientCount;
        BigInteger[][] act = new BigInteger[numRows][numKeywords];

        idx = 0;
        for (String keyword : uniqueKeywords) {
            // Encode keyword
            keywordToNumber(keyword);
            if (numericText.length() < 2 * Constant.getMaxKeywordLength()) {
                while (numericText.length() < 2 * Constant.getMaxKeywordLength())
                    numericText.append(Constant.getPadding());
            }

            act[0][idx] = new BigInteger(numericText.toString());
            act[1][idx] = BigInteger.valueOf(idx);

            // No access for all clients
            BigInteger noAccess = generateNoAccessCodeForKeyword();
            for (int c = 0; c < clientCount; c++) {
                act[2 + c][idx] = noAccess;
            }

            keywordCount++;
            idx++;
        }

        // Create shares for ACT
        BigInteger[][][] actShares = new BigInteger[4][numRows][numKeywords];
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numKeywords; col++) {
                BigInteger[] shares = shamirSecretSharingAsBigInt(
                        new StringBuilder(act[row][col].toString()), 4);
                for (int s = 0; s < 4; s++) {
                    actShares[s][row][col] = shares[s];
                }
            }
        }

        // Send ACT to all 4 servers immediately
        for (int s = 0; s < 4; s++) {
            int shareIdx = (s == 0) ? 2 : (s == 1 ? 0 : (s == 2 ? 1 : 3));
            sendToServer(actShares[shareIdx], (s + 1));
        }

        // CLEAR MEMORY
        act = null;
        actShares = null;
        System.gc();

        // ========================================================================
        // PHASE 3 & 4: BUILD ADDR AND OPT_INV (Fixed 50k capacity per keyword)
        // ========================================================================

        // ✅ Initialize opt_inv with fixed size: numKeywords rows × 50008 columns
        int optInvSize = numKeywords * SLOTS_PER_KEYWORD;

        long[] optInv = new long[optInvSize];
        int sizeIIOPT = 0;

        // ✅ Initialize ADDR table
        int addrEntryLength = 2 + 2 * Constant.getHashBlockCount();
        long[][] addr = new long[numKeywords][addrEntryLength];

        // ========================================================================
        // BUILD OPT_INV AND ADDR - ONE ROW PER KEYWORD
        // ========================================================================
        System.out.println("\nBuilding inverted index and ADDR table...");

        idx = 0;
        for (String keyword : uniqueKeywords) {
            List<Integer> fileIds = keywordToFiles.get(keyword);

            // Each keyword starts at: idx × SLOTS_PER_KEYWORD
            // Example: keyword 0 → position 0
            // keyword 1 → position 50008
            // keyword 2 → position 100016
            int startPos = idx * SLOTS_PER_KEYWORD;

            // Initialize ADDR entry
            addr[idx][0] = startPos;
            addr[idx][1] = Constant.getHashBlockCount(); // Will be updated below

            // ====================================================================
            // STORE HASH BLOCKS (positions startPos to startPos+7)
            // ====================================================================
            StringBuilder hashInput = new StringBuilder();

            // Build hash input from file IDs
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                hashInput.append("0");
                while (hashInput.length() < (i + 1) * Constant.getHashBlockSize()) {
                    hashInput.insert(i * Constant.getHashBlockSize(), "0");
                }
            }

            for (int fid : fileIds) {
                hashInput.append(fid);
            }

            System.out.println(hashInput.toString());

            String hash = hashDigest(hashInput.toString());
            long[] hashBlocks = getHashBlocks(hash);

            // Store hash blocks at beginning of keyword's row
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                optInv[sizeIIOPT++] = hashBlocks[i];
            }

            // ====================================================================
            // STORE FILE IDs (positions startPos+8 onwards)
            // ====================================================================
            for (int fid : fileIds) {
                optInv[sizeIIOPT++] = fid;
            }

            // ====================================================================
            // PAD REMAINING SLOTS WITH ZEROS (up to 50,000 file slots)
            // ====================================================================
            int filesStored = fileIds.size();
            int paddingNeeded = 10000 - filesStored;

            for (int i = 0; i < paddingNeeded; i++) {
                optInv[sizeIIOPT++] = 0;
            }

            // ====================================================================
            // UPDATE ADDR WITH HASHES
            // ====================================================================

            // Hash of (startPos, count)
            String hashAC = hashDigest(hashDigest(String.valueOf(startPos)) +
                    (Constant.getHashBlockCount() + filesStored));
            long[] hashBlocksAC = getHashBlocks(hashAC);
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                addr[idx][2 + i] = hashBlocksAC[i];
            }

            // Hash of position vector (initially empty)
            for (int i = 0; i < Constant.getHashBlockCount(); i++) {
                addr[idx][2 + Constant.getHashBlockCount() + i] = 0;
            }

            // Update ADDR count to include actual file count
            addr[idx][1] = Constant.getHashBlockCount() + filesStored;

            idx++;
        }

        // ========================================================================
        // SET DIMENSIONS
        // ========================================================================
        optCol = colSize; // 50008
        int optRow = numKeywords; // One row per keyword

        // ========================================================================
        // CREATE SHARES FOR OPT_INV
        // ========================================================================
        System.out.println("\nCreating secret shares for inverted index...");

        serverCount = 3;

        long[][] optInvShares = new long[3][optInvSize];
        for (int col = 0; col < optInvSize; col++) {
            long[] shares = shamirSecretSharingAsLong(optInv[col], 3);
            for (int s = 0; s < 3; s++) {
                optInvShares[s][col] = shares[s];
            }
        }

        System.out.println("✓ Inverted index shares created");
        System.out.println("  Share memory: " + String.format("%.2f MB", optInvSize * 8.0 * 3 / 1024 / 1024));

        for (int s = 0; s < 3; s++)
            sendToServer(optInvShares[s], (s + 1));

        // CLEAR MEMORY
        optInv = null;
        optInvShares = null;
        System.gc();

        // ========================================================================
        // CREATE SHARES FOR ADDR
        // ========================================================================
        System.out.println("Creating secret shares for ADDR table...");

        long[][][] addrShares = new long[3][numKeywords][addrEntryLength];
        for (int k = 0; k < numKeywords; k++) {
            for (int i = 0; i < addrEntryLength; i++) {
                long[] shares = shamirSecretSharingAsLong(addr[k][i], 3);
                for (int s = 0; s < 3; s++) {
                    addrShares[s][k][i] = shares[s];
                }
            }
        }

        System.out.println("✓ ADDR shares created");

        for (int s = 0; s < 3; s++) {
            int shareIdx = (s == 0) ? 2 : (s == 1 ? 0 : 1);
            sendToServer(addrShares[shareIdx], (s + 1));
        }

        // CLEAR MEMORY
        addr = null;
        addrShares = null;
        System.gc();

        // ========================================================================
        // SEND TO SERVERS
        // ========================================================================
        System.out.println("\n[PHASE 5+6] Sending data to servers...");

        int BATCH_SIZE = 5000;
        int totalBatches = (int) Math.ceil((double) files.size() / BATCH_SIZE);

        // Process and send files batch by batch
        for (int batch = 0; batch < totalBatches; batch++) {
            int startIdx = batch * BATCH_SIZE;
            int endIdx = Math.min(startIdx + BATCH_SIZE, files.size());
            int batchSize = endIdx - startIdx;

            System.out.println("\n[Batch " + (batch + 1) + "/" + totalBatches + "] Processing files " +
                    startIdx + "-" + (endIdx - 1) + "...");

            long[][][] batchContentShares = new long[3][batchSize][];
            long[][][] batchKeywordShares = new long[3][batchSize][];

            for (int i = startIdx; i < endIdx; i++) {
                int batchIdx = i - startIdx;
                FileUpload file = files.get(i);

                // Encrypt file content
                long[] encrypted = encryptFile(file.content);

                // Create keyword list
                int pad;
                int actualKeywordCount = file.keywords.length;

                if (actualKeywordCount <= checkpoint2) {
                    pad = checkpoint2 - actualKeywordCount;
                } else if (actualKeywordCount <= checkpoint1) {
                    pad = checkpoint1 - actualKeywordCount;
                } else {
                    pad = 0;
                }

                int kwListLength = Constant.getHashBlockCount() + actualKeywordCount + pad;
                long[] kwList = new long[kwListLength];

                String fileHash = hashDigest(file.content);
                long[] hashBlocks = getHashBlocks(fileHash);
                System.arraycopy(hashBlocks, 0, kwList, 0, Constant.getHashBlockCount());

                for (int j = 0; j < actualKeywordCount; j++) {
                    Integer keywordIndex = keywordIndexMap.get(file.keywords[j]);
                    if (keywordIndex != null) {
                        kwList[Constant.getHashBlockCount() + j] = keywordIndex + 1;
                    }
                }

                // Create shares for file content
                batchContentShares[0][batchIdx] = new long[encrypted.length];
                batchContentShares[1][batchIdx] = new long[encrypted.length];
                batchContentShares[2][batchIdx] = new long[encrypted.length];

                for (int j = 0; j < encrypted.length; j++) {
                    long[] shares = shamirSecretSharingAsLong(encrypted[j], 3);
                    for (int s = 0; s < 3; s++) {
                        batchContentShares[s][batchIdx][j] = shares[s];
                    }
                }

                // Create shares for keyword list
                batchKeywordShares[0][batchIdx] = new long[kwList.length];
                batchKeywordShares[1][batchIdx] = new long[kwList.length];
                batchKeywordShares[2][batchIdx] = new long[kwList.length];

                for (int j = 0; j < kwList.length; j++) {
                    long[] shares = shamirSecretSharingAsLong(kwList[j], 3);
                    for (int s = 0; s < 3; s++) {
                        batchKeywordShares[s][batchIdx][j] = shares[s];
                    }
                }

                if ((i + 1) % 1000 == 0) {
                    System.out.println("  Created shares for " + (i + 1) + "/" + files.size() + " files");
                }
            }

            System.out.println("  ✓ Batch processing complete");

            // Send batch to servers
            for (int s = 0; s < 3; s++) {
                int shareIndex;
                if (s == 0)
                    shareIndex = 2;
                else if (s == 1)
                    shareIndex = 0;
                else
                    shareIndex = 1;

                sendToServer(batchContentShares[shareIndex], (s + 1));
                sendToServer(batchKeywordShares[shareIndex], (s + 1));
            }

            System.out.println("  ✓ Batch sent to all servers");

            batchContentShares = null;
            batchKeywordShares = null;

            if (batch % 10 == 0) {
                System.gc();
            }
        }

        // ========================================================================
        // FINAL SUMMARY
        // ========================================================================
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BULK INITIALIZATION COMPLETE");
        System.out.println("=".repeat(80));
        System.out.println("Time: " + (totalTime / 1000.0) + "s (" + (totalTime / 60000.0) + " min)");
        System.out.println("Keywords: " + numKeywords + " (capacity: 50k files each)");
        System.out.println("Files: " + files.size());
        System.out.println("Inverted index: " + optRow + " x " + optCol + " = " + optInvSize + " slots");
        System.out.println("Memory: " + String.format("%.2f MB", optInvSize * 8.0 / 1024 / 1024));
        System.out.println("=".repeat(80));

        // Update client state
        this.keywordCount = numKeywords;
        this.fileCount = files.size();
        this.optRow = numKeywords; // Each keyword is one row
        this.optCol = colSize; // 50008 columns per row

        generateNoAccessCodes();

        return "SUCCESS";
    }

    public String getFile(int fileId) throws IOException, ClassNotFoundException {
        for (int i = 0; i < 3; i++) {
            sendToServer("GET_FILE", (i + 1));
        }
        for (int i = 0; i < 3; i++) {
            sendToServer(fileId, (i + 1));
        }

        List<Object> objects = startClient(3);
        long[][] fileShares = new long[3][];

        for (Object obj : objects) {
            FileShare message = (FileShare) obj;
            int serverId = message.getServerNumber(); // Assuming getPort() returns the server number
            long[] sharePayload = (long[]) message.getFileShare();

            // Place the share in the correct index (server numbers are 1-3, arrays are 0-2)
            fileShares[serverId] = sharePayload;
        }

        return extractFileContent(fileShares);
    }

    public String extractFileContent(long[][] fileShares) {
        long[] reconstructedFile = reconstructFile(fileShares);

        // Step 4: Separate hash digest from numeric content
        // First hashBlockCount elements = hash, rest = numeric content
        int hashBlockCount = Constant.getHashBlockCount();
        String numericContent = extractNumericContent(reconstructedFile, hashBlockCount);

        // Step 5: Decrypt numeric string back to original text
        String originalText = decrypt_numeric_string(numericContent);

        return originalText;
    }

    /**
     * Extract numeric content from reconstructed file data.
     * This version correctly pads each number chunk with leading zeros.
     */
    private String extractNumericContent(long[] reconstructedFile, int hashBlockCount) {
        StringBuilder numericContent = new StringBuilder();

        // Get the block size you used during the original encoding.
        int blockSize = Constant.getHashBlockSize();

        // Skip the hash digest blocks and extract the rest.
        for (int i = hashBlockCount; i < reconstructedFile.length; i++) {
            long numberChunk = reconstructedFile[i];

            // Format each long back into a string, padding with leading zeros
            // to restore the original chunk size. For example, if blockSize is 8,
            // the long 708 becomes the string "00000708".
            String formattedChunk = String.format("%0" + blockSize + "d", numberChunk);

            numericContent.append(formattedChunk);
        }

        // Now, you need to trim the final string to the original numeric content
        // length,
        // as the last chunk might have been padded. This requires getting the original
        // file's numeric length, which is part of the 'files' data structure.
        // For now, this fix will get you much closer, but a final trim might be needed
        // depending on your exact 'DataOutsource' logic.

        return numericContent.toString();
    }

    private long[] reconstructFile(long[][] fileShares) {
        // fileShares[0] = Server 1's shares for the file
        // fileShares[1] = Server 2's shares for the file
        // fileShares[2] = Server 3's shares for the file

        int fileSize = fileShares[0].length;
        long[] reconstructed = new long[fileSize];

        // For each element in the file
        for (int elementIndex = 0; elementIndex < fileSize; elementIndex++) {
            // Gather shares from all 3 servers for this element
            long[] share = new long[3];
            share[0] = fileShares[0][elementIndex]; // Server 1's share for element
            share[1] = fileShares[1][elementIndex]; // Server 2's share for element
            share[2] = fileShares[2][elementIndex]; // Server 3's share for element

            // Use Lagrange interpolation to recover original value
            reconstructed[elementIndex] = lagrangeInterpolation(share);
        }

        return reconstructed;
    }

    /**
     * Lagrange interpolation for 3 shares
     * Formula for 3-of-3 scheme:
     * secret = (3 × share[0] - 3 × share[1] + share[2]) mod p
     */
    public long lagrangeInterpolation(long[] share) {
        long modValue = Constant.getModParameter();
        return switch (share.length) {
            case 2 -> Math.floorMod((((2 * share[0]) % modValue) - share[1]), modValue);
            case 3 -> (Math.floorMod((((3 * share[0]) % modValue) -
                    ((3 * share[1]) % modValue)), modValue) + share[2]) % modValue;
            default -> throw new IllegalStateException("Unexpected value: " + share.length);
        };
    }

    // Map to hold persistent connections: Key = Server ID (1-4), Value = Connection
    private Map<Integer, DBOConnection> connections = new HashMap<>();

    /**
     * Performs initialization tasks like reading from the properties files, initial
     * memory allocation etc
     *
     * @throws IOException
     */
    @PostConstruct
    public void initialization()
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        // reading the properties file for client

        serverVerification = Boolean.parseBoolean(commonProperties.getProperty("serverVerification"));
        clientVerification = Boolean.parseBoolean(commonProperties.getProperty("clientVerification"));
        numThreads = Integer.parseInt(commonProperties.getProperty("numThreads"));
        // fileCount = Integer.parseInt(commonProperties.getProperty("maxDocs"));
        optRow = Integer.parseInt(commonProperties.getProperty("optRow"));
        optCol = Integer.parseInt(commonProperties.getProperty("optCol"));
        seedDBO = Integer.parseInt(dboProperties.getProperty("seedDBO"));
        fileLength = Integer.parseInt(commonProperties.getProperty("maxFileLength"));
        checkpoint1 = Integer.parseInt(outsourceProperties.getProperty("checkpoint1"));
        checkpoint2 = Integer.parseInt(outsourceProperties.getProperty("checkpoint2"));

        // reading each server Ip and port numbers
        serverCount = 4;
        serverIP = new String[serverCount];
        serverPort = new int[serverCount];

        System.out.println("DBO: Initializing persistent connections to " + serverCount + " servers...");

        // ESTABLISH PERSISTENT CONNECTIONS
        for (int i = 0; i < serverCount; i++) {
            int serverId = i + 1;
            serverIP[i] = commonProperties.getProperty("serverIP" + serverId);
            serverPort[i] = Integer.parseInt(commonProperties.getProperty("serverPort" + serverId));

            try {
                System.out.println("Connecting to Server " + serverId + " at " + serverIP[i] + ":" + serverPort[i]);
                DBOConnection conn = new DBOConnection(serverId, serverIP[i], serverPort[i]);
                connections.put(serverId, conn);
                conn.send(new DBORequest("CONNECT"));
                System.out.println("✓ Connected to Server " + serverId);
            } catch (IOException e) {
                System.err.println("✗ Failed to connect to Server " + serverId + ": " + e.getMessage());
                throw e; // Fail fast if DBO cannot connect on startup
            }
        }

        servers_1_2 = new BigInteger[numThreads];
        servers_2_3 = new BigInteger[numThreads];
        servers_3_1 = new BigInteger[numThreads];

        keywordCount = getKeywordCountFromServers();
        System.out.println("Keyword count from servers: " + keywordCount);

        // Generate noAccessCodes for the ACTUAL keyword count
        if (keywordCount > 0) {
            generateNoAccessCodes();
        } else {
            noAccessCode = new BigInteger[0];
            System.out.println("✓ No keywords in system yet");
        }

        System.out.println("Number of thread:" + numThreads);
        System.out.println("serverVerification:" + serverVerification);
        System.out.println("clientVerification:" + clientVerification);
    }

    @Override
    public void close() throws IOException {
        if (ss != null && !ss.isClosed()) {
            ss.close();
            System.out.println("DBO socket closed.");
        }
    }

    public boolean resetServers() {
        System.out.println("Resetting all servers...");

        for (int i = 0; i < serverCount; i++) {
            int serverId = i + 1;
            try {
                DBOConnection conn = connections.get(serverId);
                if (conn != null) {
                    conn.send(new DBORequest("RESET_SERVER"));
                    System.out.println("Sent RESET_SERVER to Server " + serverId);
                    try {
                        String response = conn.receive().toString();
                        if (response.equals("SUCCESS")) {
                            System.out.println("✓ Received SUCCESS response from Server " + serverId);
                        } else {
                            System.err.println("✗ Received FAIL response from Server " + serverId);
                            return false;
                        }
                    } catch (Exception e) {
                        System.err.println(
                                "No response from Server " + serverId + " after RESET_SERVER: " + e.getMessage());
                        return false;
                    }

                } else {
                    System.err.println("No connection found for Server " + serverId);
                    return false;
                }
            } catch (IOException e) {
                System.err.println("Failed to send RESET_SERVER to Server " + serverId + ": " + e.getMessage());
                return false;
            }

        }

        // Reset local state
        keywordCount = 0;
        fileCount = 0;
        optRow = 0;
        optCol = 0;
        noAccessCode = new BigInteger[0];

        System.out.println("All servers reset.");
        return true;
    }
}