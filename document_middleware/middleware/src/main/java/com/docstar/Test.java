
package com.docstar;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.docstar.middleware.SearchingClient;

class Test {

    public static void main(String[] args) {
        // Start 3 SearchingClients in parallel
        ExecutorService testPool = Executors.newFixedThreadPool(3);

        for (int i = 0; i < 3; i++) {
            final int clientNum = i;
            testPool.submit(() -> {
                SearchingClient client = new SearchingClient();

                try {
                    client.initialization();
                    client.setSessionId("test" + UUID.randomUUID().toString());
                    switch (clientNum) {
                        case 0:
                            client.runSearch("deal", Integer.toString(clientNum + 1), false, true);
                            break;
                        case 1:
                            client.runSearch("sell", Integer.toString(clientNum + 1), false, true);
                            break;
                        case 2:
                            client.runSearch("trade", Integer.toString(clientNum + 1), false, true);
                            break;
                    }

                    System.out.println(
                            "Finished searching with session ID: " + " phase2Result: " + client.getPhase2Result());
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    ex.printStackTrace();
                }
            });
        }
        testPool.shutdown();
    }
}