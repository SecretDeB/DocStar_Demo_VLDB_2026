package com.docstar.utility;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

import com.docstar.middleware.Constant;
import com.docstar.service.StopwordLoader;

public class ExtractKeywordsUtil {

    /**
     * Extract keywords from content (reuse your existing logic)
     */
    public static Map<String, Integer> extractKeywordsWithCountFromContent(String content) {

        SnowballStemmer stemmer = new englishStemmer();
        Map<String, Integer> keywords = new LinkedHashMap<>();

        // Tokenize
        String[] tokens = content.split("[.;:!?/\\\\,#@$&)(\" =\\n\\r\\t]");

        for (String token : tokens) {
            token = token.toLowerCase().trim();

            Set<String> stopwords = StopwordLoader.getStopwords();

            if (stopwords.contains(token)) {
                continue;
            }

            // Check if valid keyword
            if (token.length() >= Constant.getMinKeywordLength()
                    && token.length() <= Constant.getMaxKeywordLength()
                    && token.matches("[a-z]+")) {

                // Stem the token
                stemmer.setCurrent(token);
                stemmer.stem();
                String stemmed = stemmer.getCurrent();

                keywords.put(stemmed, keywords.getOrDefault(stemmed, 0) + 1);
            }
        }

        // Sort by frequency
        keywords = keywords.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));

        return keywords;
    }

    /**
     * Extract keywords from content (reuse your existing logic)
     */
    public static Set<String> extractKeywordsFromContent(String content) {
        SnowballStemmer stemmer = new englishStemmer();
        Set<String> keywords = new LinkedHashSet<>();

        // Tokenize
        String[] tokens = content.split("[.;:!?/\\\\,#@$&)(\" =\\n\\r\\t]");

        for (String token : tokens) {
            token = token.toLowerCase().trim();

            Set<String> stopwords = StopwordLoader.getStopwords();

            if (stopwords.contains(token)) {
                continue;
            }

            // Check if valid keyword
            if (token.length() >= Constant.getMinKeywordLength()
                    && token.length() <= Constant.getMaxKeywordLength()
                    && token.matches("[a-z]+")) {

                // Stem the token
                stemmer.setCurrent(token);
                stemmer.stem();
                String stemmed = stemmer.getCurrent();

                keywords.add(stemmed);
            }
        }

        return keywords;
    }
}
