package com.docstar.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StopwordLoader {

    private static Set<String> stopwords;

    static {
        stopwords = new HashSet<>();
        try {
            InputStream is = StopwordLoader.class.getClassLoader()
                    .getResourceAsStream("stopwords.txt");

            if (is != null) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim().toLowerCase();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        stopwords.add(line);
                    }
                }
                reader.close();
                System.out.println("Loaded " + stopwords.size() + " stopwords");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Set<String> getStopwords() {
        return stopwords;
    }
}