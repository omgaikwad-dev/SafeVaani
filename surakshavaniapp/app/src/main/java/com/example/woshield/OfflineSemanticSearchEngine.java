package com.example.woshield;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OfflineSemanticSearchEngine — Natural Language / Semantic Place Search for Offline Devices.
 *
 * Designed to execute completely client-side without any internet connection or cloud API calls.
 *
 * 4-Stage Operational Pipeline:
 * 1. User Query (e.g. "Find a hospital near Hadapsar")
 * 2. Regex Engine:
 *      - Extracts Record IDs (PUN001, NAG051, etc.)
 *      - Extracts coordinates (lat, lng pairs)
 *      - Extracts phone / emergency helpline numbers (100, 108, 112, 10-digit mobile)
 *      - Extracts category intents (hospital, police, pharmacy, transport, fuel, food)
 *      - Extracts known locality anchors (Hadapsar, Swargate, Kothrud, Sitabuldi, etc.)
 * 3. Cosine Similarity Engine:
 *      - TF-IDF / Term Frequency vectorizer over query and candidate documents (name, area, description, subtype)
 *      - Computes dot product normalized by Euclidean L2 magnitude
 * 4. Structured Filters + Distance Ranking:
 *      - Enforces category filtering (e.g. category == 'hospital')
 *      - Applies verification weights (KNOWN = 1.0, PROBABLE = 0.85, OUTDATED = 0.65, UNKNOWN = 0.50)
 *      - Computes Haversine distance from user location or extracted target locality
 *      - Returns ranked relevant results with record IDs, coordinates, and navigation triggers.
 */
public class OfflineSemanticSearchEngine {

    private static final String TAG = "OfflineSearchEngine";

    // Stop words to filter out before cosine vectorization
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "all", "am", "an", "and", "any", "are", "as", "at",
            "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "did", "do", "does", "doing", "down", "during", "each", "few", "find", "for", "from",
            "get", "give", "go", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
            "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself",
            "just", "locate", "look", "me", "more", "most", "my", "myself", "near", "nearby", "nearest",
            "need", "no", "nor", "not", "now", "of", "off", "on", "once", "only", "open", "or", "other",
            "our", "ours", "ourselves", "out", "over", "own", "place", "places", "please", "search",
            "show", "so", "some", "such", "tell", "than", "that", "the", "their", "theirs", "them",
            "themselves", "then", "there", "these", "they", "this", "those", "through", "to", "too",
            "under", "until", "up", "very", "want", "was", "we", "were", "what", "when", "where",
            "which", "while", "who", "whom", "why", "will", "with", "would", "you", "your", "yours"
    ));

    // Regex Patterns
    private static final Pattern PATTERN_RECORD_ID = Pattern.compile("\\b(PUN|NAG)[0-9]{3}\\b|\\b(PUN|NAG)_[A-Z0-9_]+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_COORDINATES = Pattern.compile("([-+]?\\d{1,2}\\.\\d{3,})\\s*[, ]\\s*([-+]?\\d{1,3}\\.\\d{3,})");
    private static final Pattern PATTERN_PHONE = Pattern.compile("\\b(?:\\+?91|0)?[6-9]\\d{9}\\b|\\b10[0-9]\\b|\\b112\\b");

    // Category Regex
    private static final Pattern PATTERN_CAT_HOSPITAL = Pattern.compile("\\b(hospital|hospitals|clinic|clinics|doctor|doctors|emergency|icu|casualty|medical care|arogya|dispensary|healthcare)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CAT_POLICE = Pattern.compile("\\b(police|chowki|station|thana|cop|cops|patrol|beat|security|suraksha|commissionerate|outpost)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CAT_PHARMACY = Pattern.compile("\\b(pharmacy|pharmacies|chemist|chemists|medical store|medicine|medicines|dawa|aushadh|24x7 chemist)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CAT_TRANSPORT = Pattern.compile("\\b(bus|buses|metro|transit|ratrani|train|railway|auto|rickshaw|terminal|depot|stop|station)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CAT_FUEL = Pattern.compile("\\b(petrol|fuel|diesel|cng|pump|gas station|hp|iocl|bpcl|shell)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CAT_FOOD = Pattern.compile("\\b(food|dhaba|hotel|cafe|canteen|tea|chai|snacks|eatery|restaurant|mess)\\b", Pattern.CASE_INSENSITIVE);

    // Known Locality Anchors (Pune & Nagpur)
    private static final Map<String, double[]> KNOWN_LOCALITIES = new HashMap<>();
    static {
        // Pune Localities & Landmarks
        KNOWN_LOCALITIES.put("hadapsar", new double[]{18.5018, 73.9260});
        KNOWN_LOCALITIES.put("magarpatta", new double[]{18.5089, 73.9260});
        KNOWN_LOCALITIES.put("amanora", new double[]{18.5089, 73.9260});
        KNOWN_LOCALITIES.put("swargate", new double[]{18.5018, 73.8636});
        KNOWN_LOCALITIES.put("kothrud", new double[]{18.5074, 73.8077});
        KNOWN_LOCALITIES.put("shivajinagar", new double[]{18.5327, 73.8496});
        KNOWN_LOCALITIES.put("katraj", new double[]{18.4529, 73.8586});
        KNOWN_LOCALITIES.put("dhankawadi", new double[]{18.4680, 73.8540});
        KNOWN_LOCALITIES.put("hinjawadi", new double[]{18.5913, 73.7389});
        KNOWN_LOCALITIES.put("hinjewadi", new double[]{18.5913, 73.7389});
        KNOWN_LOCALITIES.put("wakad", new double[]{18.5990, 73.7625});
        KNOWN_LOCALITIES.put("pimple saudagar", new double[]{18.5987, 73.7978});
        KNOWN_LOCALITIES.put("baner", new double[]{18.5590, 73.7868});
        KNOWN_LOCALITIES.put("balewadi", new double[]{18.5750, 73.7710});
        KNOWN_LOCALITIES.put("kharadi", new double[]{18.5515, 73.9398});
        KNOWN_LOCALITIES.put("viman nagar", new double[]{18.5679, 73.9143});
        KNOWN_LOCALITIES.put("vimannagar", new double[]{18.5679, 73.9143});
        KNOWN_LOCALITIES.put("kalyani nagar", new double[]{18.5463, 73.9033});
        KNOWN_LOCALITIES.put("koregaon park", new double[]{18.5362, 73.8940});
        KNOWN_LOCALITIES.put("aundh", new double[]{18.5580, 73.8075});
        KNOWN_LOCALITIES.put("yerawada", new double[]{18.5524, 73.8870});
        KNOWN_LOCALITIES.put("yerwada", new double[]{18.5524, 73.8870});
        KNOWN_LOCALITIES.put("camp", new double[]{18.5126, 73.8792});
        KNOWN_LOCALITIES.put("deccan", new double[]{18.5186, 73.8415});
        KNOWN_LOCALITIES.put("fc road", new double[]{18.5186, 73.8415});
        KNOWN_LOCALITIES.put("jm road", new double[]{18.5230, 73.8470});
        KNOWN_LOCALITIES.put("warje", new double[]{18.4790, 73.8010});
        KNOWN_LOCALITIES.put("karve nagar", new double[]{18.4878, 73.8152});
        KNOWN_LOCALITIES.put("pimpri", new double[]{18.6220, 73.8050});
        KNOWN_LOCALITIES.put("chinchwad", new double[]{18.6298, 73.7997});
        KNOWN_LOCALITIES.put("pune station", new double[]{18.5289, 73.8743});
        KNOWN_LOCALITIES.put("pune railway", new double[]{18.5289, 73.8743});
        KNOWN_LOCALITIES.put("erandwane", new double[]{18.5089, 73.8336});
        KNOWN_LOCALITIES.put("rasta peth", new double[]{18.5169, 73.8664});
        KNOWN_LOCALITIES.put("chakan", new double[]{18.7600, 73.8500});
        KNOWN_LOCALITIES.put("pune", new double[]{18.5204, 73.8567});

        // Nagpur Localities & Landmarks
        KNOWN_LOCALITIES.put("sitabuldi", new double[]{21.14139, 79.08248});
        KNOWN_LOCALITIES.put("dharampeth", new double[]{21.1435, 79.0620});
        KNOWN_LOCALITIES.put("ganeshpeth", new double[]{21.1450, 79.0890});
        KNOWN_LOCALITIES.put("khapri", new double[]{21.0493, 79.0478});
        KNOWN_LOCALITIES.put("mihan", new double[]{21.0493, 79.0478});
        KNOWN_LOCALITIES.put("hingna", new double[]{21.1050, 78.9990});
        KNOWN_LOCALITIES.put("itwari", new double[]{21.1560, 79.1080});
        KNOWN_LOCALITIES.put("ajni", new double[]{21.1280, 79.0830});
        KNOWN_LOCALITIES.put("it park", new double[]{21.1255, 79.0515});
        KNOWN_LOCALITIES.put("gayatri nagar", new double[]{21.1255, 79.0515});
        KNOWN_LOCALITIES.put("medical square", new double[]{21.1290, 79.0850});
        KNOWN_LOCALITIES.put("civil lines", new double[]{21.1539, 79.0790});
        KNOWN_LOCALITIES.put("ambazari", new double[]{21.1306, 79.0475});
        KNOWN_LOCALITIES.put("sadar", new double[]{21.1620, 79.0810});
        KNOWN_LOCALITIES.put("cotton market", new double[]{21.1442, 79.0915});
        KNOWN_LOCALITIES.put("nagpur station", new double[]{21.1524, 79.0889});
        KNOWN_LOCALITIES.put("nagpur railway", new double[]{21.1524, 79.0889});
        KNOWN_LOCALITIES.put("nagpur", new double[]{21.1458, 79.0882});
    }

    /**
     * Search Result Model with complete matching metrics.
     */
    public static class SearchResult implements Comparable<SearchResult> {
        public NightOwlRecord record;
        public double finalScore;
        public double cosineSimilarity;
        public double regexScore;
        public double distanceKm;
        public double verificationWeight;
        public boolean categoryMatched;
        public String extractedCategory;
        public String extractedLocality;

        @Override
        public int compareTo(SearchResult other) {
            // Higher score first
            return Double.compare(other.finalScore, this.finalScore);
        }
    }

    /**
     * Parsed query intent and extracted entities from Regex.
     */
    public static class ExtractedQueryIntent {
        public String rawQuery;
        public String extractedRecordId = null;
        public double[] extractedCoordinates = null;
        public String extractedPhone = null;
        public String targetCategory = null;
        public String targetLocality = null;
        public double[] targetLocalityCoords = null;
        public List<String> queryTokens = new ArrayList<>();
    }

    /**
     * Main entry point: Executes 4-step offline semantic search against the 100 seed records.
     *
     * @param context Android context to access assets/repository
     * @param userQuery Natural language query (e.g. "Find a hospital near Hadapsar")
     * @param userLat User's current GPS latitude (or fallback)
     * @param userLng User's current GPS longitude (or fallback)
     * @param maxResults Maximum results to return (e.g. 5)
     * @return List of ranked SearchResult objects
     */
    public static List<SearchResult> executeSearch(
            Context context,
            String userQuery,
            double userLat,
            double userLng,
            int maxResults) {

        if (TextUtils.isEmpty(userQuery)) {
            return Collections.emptyList();
        }

        // 1. Get Seed Records from CSV Repository
        NightOwlCsvRepository repo = NightOwlCsvRepository.getInstance(context);
        List<NightOwlRecord> allRecords = repo.getAllRecords();
        if (allRecords == null || allRecords.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Stage 1: Regex Entity Extraction
        ExtractedQueryIntent intent = parseQueryWithRegex(userQuery);

        // 3. Stage 2: Cosine Similarity Vectorization
        Map<String, Double> queryVector = computeTermFrequencyVector(intent.queryTokens);

        // Determine reference coordinates for distance ranking:
        // If query explicitly mentioned a locality (e.g. "Hadapsar"), use that locality's anchor coordinates!
        // Otherwise use user's active GPS coordinates.
        double refLat = (intent.targetLocalityCoords != null) ? intent.targetLocalityCoords[0] : userLat;
        double refLng = (intent.targetLocalityCoords != null) ? intent.targetLocalityCoords[1] : userLng;

        // 4. Stage 3: Structured Filters + Distance Ranking
        List<SearchResult> results = new ArrayList<>();

        for (NightOwlRecord record : allRecords) {
            SearchResult res = evaluateRecord(record, intent, queryVector, refLat, refLng);
            if (res != null && res.finalScore > 0.05) {
                results.add(res);
            }
        }

        // Sort descending by finalScore
        Collections.sort(results);

        // Limit results
        if (results.size() > maxResults) {
            return new ArrayList<>(results.subList(0, maxResults));
        }
        return results;
    }

    /**
     * Stage 1: Regex Extraction
     * Extracts record IDs, coordinates, phones, category intents, and locality anchors.
     */
    public static ExtractedQueryIntent parseQueryWithRegex(String query) {
        ExtractedQueryIntent intent = new ExtractedQueryIntent();
        intent.rawQuery = query;
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();

        // 1. Check for specific Record ID
        Matcher idMatcher = PATTERN_RECORD_ID.matcher(query);
        if (idMatcher.find()) {
            intent.extractedRecordId = idMatcher.group(0).toUpperCase(Locale.ROOT);
        }

        // 2. Check for explicit coordinates
        Matcher coordMatcher = PATTERN_COORDINATES.matcher(query);
        if (coordMatcher.find()) {
            try {
                double lat = Double.parseDouble(coordMatcher.group(1));
                double lng = Double.parseDouble(coordMatcher.group(2));
                if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                    intent.extractedCoordinates = new double[]{lat, lng};
                }
            } catch (Exception ignored) {}
        }

        // 3. Check for phone number
        Matcher phoneMatcher = PATTERN_PHONE.matcher(query);
        if (phoneMatcher.find()) {
            intent.extractedPhone = phoneMatcher.group(0);
        }

        // 4. Extract Category Intent
        if (PATTERN_CAT_HOSPITAL.matcher(lowerQuery).find()) {
            intent.targetCategory = "hospital";
        } else if (PATTERN_CAT_POLICE.matcher(lowerQuery).find()) {
            intent.targetCategory = "public_facility"; // in CSV, police is under public_facility or subtype
        } else if (PATTERN_CAT_PHARMACY.matcher(lowerQuery).find()) {
            intent.targetCategory = "pharmacy";
        } else if (PATTERN_CAT_TRANSPORT.matcher(lowerQuery).find()) {
            intent.targetCategory = "transport";
        } else if (PATTERN_CAT_FUEL.matcher(lowerQuery).find()) {
            intent.targetCategory = "fuel";
        } else if (PATTERN_CAT_FOOD.matcher(lowerQuery).find()) {
            intent.targetCategory = "food";
        }

        // 5. Extract Known Locality Anchors (check longest keys first to prefer "pune station" over "pune")
        String matchedLocality = null;
        int maxLen = 0;
        for (Map.Entry<String, double[]> entry : KNOWN_LOCALITIES.entrySet()) {
            if (lowerQuery.contains(entry.getKey()) && entry.getKey().length() > maxLen) {
                matchedLocality = entry.getKey();
                maxLen = entry.getKey().length();
            }
        }
        if (matchedLocality != null) {
            intent.targetLocality = matchedLocality;
            intent.targetLocalityCoords = KNOWN_LOCALITIES.get(matchedLocality);
        }

        // 6. Tokenize for Cosine Similarity
        String[] rawTokens = lowerQuery.replaceAll("[^a-zA-Z0-9]", " ").split("\\s+");
        for (String t : rawTokens) {
            String token = t.trim();
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                intent.queryTokens.add(token);
            }
        }

        return intent;
    }

    /**
     * Stage 2 & 3: Evaluates candidate record with Cosine Similarity, Structured Filters, and Proximity.
     */
    private static SearchResult evaluateRecord(
            NightOwlRecord record,
            ExtractedQueryIntent intent,
            Map<String, Double> queryVector,
            double refLat,
            double refLng) {

        SearchResult res = new SearchResult();
        res.record = record;
        res.extractedCategory = intent.targetCategory;
        res.extractedLocality = intent.targetLocality;

        // Strict City Coherence Filter: Prevent cross-city leakage (e.g. Pune vs Nagpur)
        String lowerQuery = intent.rawQuery != null ? intent.rawQuery.toLowerCase(Locale.ROOT) : "";
        String recCity = record.city != null ? record.city.toLowerCase(Locale.ROOT) : "";
        if (lowerQuery.contains("pune") && !recCity.contains("pune")) {
            return null; // Exclude non-Pune records when user specified Pune
        }
        if (lowerQuery.contains("nagpur") && !recCity.contains("nagpur")) {
            return null; // Exclude non-Nagpur records when user specified Nagpur
        }

        // 1. Direct Record ID match (instant highest match)
        if (intent.extractedRecordId != null && intent.extractedRecordId.equalsIgnoreCase(record.recordId)) {
            res.regexScore = 1.0;
            res.cosineSimilarity = 1.0;
            res.categoryMatched = true;
            res.distanceKm = NightOwlCsvRepository.calculateDistanceKm(refLat, refLng, record.latitude, record.longitude);
            res.verificationWeight = getVerificationWeight(record.evidenceStatus);
            res.finalScore = 100.0;
            return res;
        }

        // 2. Structured Category Filtering
        boolean categoryMatched = false;
        double categoryBonus = 0.0;
        if (intent.targetCategory != null) {
            if ("public_facility".equalsIgnoreCase(intent.targetCategory)) {
                // Public facility: matches if category is public_facility or serviceSubtype contains police
                if ("public_facility".equalsIgnoreCase(record.category) ||
                        (record.serviceSubtype != null && record.serviceSubtype.toLowerCase().contains("police")) ||
                        record.name.toLowerCase().contains("police")) {
                    categoryMatched = true;
                    categoryBonus = 1.0;
                }
            } else if (intent.targetCategory.equalsIgnoreCase(record.category)) {
                categoryMatched = true;
                categoryBonus = 1.0;
            } else if (record.serviceSubtype != null && record.serviceSubtype.toLowerCase().contains(intent.targetCategory)) {
                categoryMatched = true;
                categoryBonus = 0.8;
            }
        } else {
            // No specific category requested
            categoryBonus = 0.3;
        }
        res.categoryMatched = categoryMatched;

        // If user specifically asked for a category (e.g. "hospital"), filter out non-matching categories
        if (intent.targetCategory != null && !categoryMatched) {
            return null; // Excluded by structured filter!
        }

        // 3. Cosine Similarity Calculation
        // Document text constructed from record metadata
        String docText = (record.name + " " +
                record.areaOrLocation + " " +
                record.category + " " +
                (record.serviceSubtype != null ? record.serviceSubtype : "") + " " +
                (record.hoursNote != null ? record.hoursNote : "")).toLowerCase(Locale.ROOT);

        Map<String, Double> docVector = computeTermFrequencyVector(tokenize(docText));
        double cosine = calculateCosineSimilarity(queryVector, docVector);
        res.cosineSimilarity = cosine;

        // 4. Locality String Match Bonus
        double localityBonus = 0.0;
        if (intent.targetLocality != null) {
            String recordArea = record.areaOrLocation.toLowerCase(Locale.ROOT);
            String recordName = record.name.toLowerCase(Locale.ROOT);
            if (recordArea.contains(intent.targetLocality) || recordName.contains(intent.targetLocality)) {
                localityBonus = 0.6; // High reward for exact locality match
            }
        }

        // 5. Distance Calculation (Haversine)
        double distanceKm = NightOwlCsvRepository.calculateDistanceKm(refLat, refLng, record.latitude, record.longitude);
        res.distanceKm = distanceKm;

        // 6. Verification Status Weight
        double verWeight = getVerificationWeight(record.evidenceStatus);
        res.verificationWeight = verWeight;

        // 7. Multi-Objective Offline Ranking Formula:
        // Score = [ (0.45 * CosineSim + 0.30 * CategoryBonus + 0.25 * LocalityBonus) * VerWeight ] / [ 1.0 + 0.12 * DistanceKm ]
        double semanticScore = (0.45 * cosine) + (0.30 * categoryBonus) + (0.25 * localityBonus);
        if (semanticScore < 0.08 && !categoryMatched) {
            return null;
        }

        // Proximity attenuation factor
        double proximityDecay = 1.0 / (1.0 + (0.12 * Math.min(distanceKm, 30.0)));
        res.finalScore = (semanticScore * 10.0) * verWeight * proximityDecay;

        return res;
    }

    /**
     * Computes the Term Frequency (TF) map from a list of tokens.
     */
    private static Map<String, Double> computeTermFrequencyVector(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens == null || tokens.isEmpty()) return tf;

        for (String token : tokens) {
            tf.put(token, tf.getOrDefault(token, 0.0) + 1.0);
        }

        // Sublinear TF scaling: 1 + ln(count)
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            entry.setValue(1.0 + Math.log(entry.getValue()));
        }
        return tf;
    }

    /**
     * Pure mathematical Cosine Similarity between two sparse vectors:
     * Cosine = (A . B) / (||A|| * ||B||)
     */
    public static double calculateCosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : v1.entrySet()) {
            String term = entry.getKey();
            if (v2.containsKey(term)) {
                dotProduct += entry.getValue() * v2.get(term);
            }
        }

        if (dotProduct <= 0.0) return 0.0;

        double norm1 = 0.0;
        for (double val : v1.values()) {
            norm1 += val * val;
        }

        double norm2 = 0.0;
        for (double val : v2.values()) {
            norm2 += val * val;
        }

        double denom = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denom <= 0.0) return 0.0;

        return dotProduct / denom;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return tokens;

        String[] raw = text.replaceAll("[^a-zA-Z0-9]", " ").split("\\s+");
        for (String t : raw) {
            String token = t.trim().toLowerCase(Locale.ROOT);
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double getVerificationWeight(String evidenceStatus) {
        if (evidenceStatus == null) return 0.50;
        switch (evidenceStatus.trim().toLowerCase(Locale.ROOT)) {
            case "known":
            case "verified":
                return 1.00;
            case "probable":
                return 0.85;
            case "outdated":
                return 0.65;
            case "unknown":
            default:
                return 0.50;
        }
    }
}
