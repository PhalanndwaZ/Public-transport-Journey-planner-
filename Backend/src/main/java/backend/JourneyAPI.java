// backend/JourneyAPI.java
package backend;

import static spark.Spark.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import spark.Request;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class JourneyAPI {
    private static final Gson gson = new Gson();
    private static final Path DATA_ROOT = Paths.get("CapeTownTransitData").toAbsolutePath().normalize();
    private static final String MULTIPART_CONFIG_ATTR = "org.eclipse.jetty.multipartConfig";
    private static final DateTimeFormatter FILE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Path TRASH_DIR = DATA_ROOT.resolve(".trash").normalize();
    private static final Path TRASH_METADATA = TRASH_DIR.resolve("metadata.json").normalize();
    private static final int TRASH_LIMIT = 3;

    /** Entry point that boots the Spark server, loads data, and wires all endpoints. */


    public static void main(String[] args) throws Exception {
        System.out.println("JourneyAPI starting...");
        int port = Integer.getInteger("PORT", 4567);
        port(port);

        enableCORS();

        final AtomicReference<TransitSystem> systemRef = new AtomicReference<>();
        try {
            systemRef.set(buildTransitSystem());
            System.out.println("TransitSystem loaded.");
        } catch (Throwable e) {
            System.err.println("Failed to load TransitSystem: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("TransitSystem present: " + (systemRef.get() != null));

        get("/health", (req, res) -> {
            res.type("application/json");
            boolean ok = systemRef.get() != null;
            return gson.toJson(Map.of("ok", ok));
        });

        get("/journey", (req, res) -> {
            res.type("application/json");

            TransitSystem system = systemRef.get();
            if (system == null) {
                res.status(500);
                return gson.toJson(Map.of("error", "Backend not initialized"));
            }

            String fromLatParam = trim(req.queryParams("fromLat"));
            String fromLngParam = trim(req.queryParams("fromLng"));
            String toLatParam = trim(req.queryParams("toLat"));
            String toLngParam = trim(req.queryParams("toLng"));
            String from = trim(req.queryParams("from"));
            String to   = trim(req.queryParams("to"));
            String time = trim(req.queryParams("time"));
            String date = trim(req.queryParams("date"));
            String modesParam = trim(req.queryParams("modes"));
            String maxWalkParam = trim(req.queryParams("maxWalkMeters"));

            if (time == null || time.isEmpty()) {
                res.status(400);
                return gson.toJson(Map.of("error", "Missing required query params: time"));
            }

            QueryPreferences preferences = QueryPreferences.fromRawInputs(
                    modesParam,
                    maxWalkParam,
                    TransitSystem.getDefaultMaxConsecutiveWalkKm(),
                    TransitSystem.getDefaultMaxCumulativeWalkKm()
            );

            if (!preferences.isValid()) {
                res.status(400);
                return gson.toJson(Map.of("error", "Invalid transport preference configuration"));
            }

            boolean hasCoordinates = fromLatParam != null && !fromLatParam.isEmpty()
                    && fromLngParam != null && !fromLngParam.isEmpty()
                    && toLatParam != null && !toLatParam.isEmpty()
                    && toLngParam != null && !toLngParam.isEmpty();

            try {
                String dayType = system.resolveDayType(date);
                boolean useCSA = preferences.requiresCSA();
                List<PathStep> path;
                if (hasCoordinates) {
                    double fromLat = Double.parseDouble(fromLatParam);
                    double fromLng = Double.parseDouble(fromLngParam);
                    double toLat = Double.parseDouble(toLatParam);
                    double toLng = Double.parseDouble(toLngParam);

                    path = system.query(fromLat, fromLng, toLat, toLng, time, date, preferences);
                } else {
                    if (from == null || to == null) {
                        res.status(400);
                        return gson.toJson(Map.of("error", "Missing required query params: coordinates or place names"));
                    }
                    path = system.query(from, to, time, date, preferences);
                }
                if (path == null || path.isEmpty()) {
                    res.status(200);
                    return gson.toJson(Map.of(
                        "routes", List.of(),
                        "message", "No route found"
                    ));
                }

                Map<String, Object> summary = computeSummary(path, dayType);
                summary.put("algorithm", useCSA ? "CSA" : "RAPTOR");
                List<Map<String, Object>> legs = buildLegs(path, system.getLoader());
                return gson.toJson(Map.of(
                    "routes", List.of(Map.of(
                        "summary", summary,
                        "steps", path,
                        "legs", legs
                    ))
                ));
            } catch (IllegalArgumentException iae) {
                res.status(400);
                return gson.toJson(Map.of("error", iae.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return gson.toJson(Map.of("error", "Internal error: " + e.getMessage()));
            }
        });

        get("/admin/schedules", (req, res) -> {
            res.type("application/json");
            try {
                ensureBaseDirectories();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("types", scheduleTypeDefinitions());
                payload.put("schedules", collectScheduleMetadata());
                payload.put("bin", recentDeletedSchedules());
                return gson.toJson(payload);
            } catch (IOException ioe) {
                res.status(500);
                return gson.toJson(Map.of("error", "Failed to inspect schedule directories"));
            }
        });

        post("/admin/schedules/add", (req, res) -> {
            res.type("application/json");
            MultipartConfigElement config = new MultipartConfigElement(System.getProperty("java.io.tmpdir"));
            req.attribute(MULTIPART_CONFIG_ATTR, config);

            Part filePart = null;
            Path tempFile = null;
            try {
                String typeParam = optionalParam(req, "type").orElse("");
                ScheduleCategory category = ScheduleCategory.fromId(typeParam)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown schedule type"));

                String filename = optionalParam(req, "filename")
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .orElseThrow(() -> new IllegalArgumentException("Missing filename"));

                filename = sanitizeFilename(filename);
                if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    throw new IllegalArgumentException("Filename must end with .csv");
                }

                filePart = req.raw().getPart("file");
                if (filePart == null || filePart.getSize() == 0) {
                    throw new IllegalArgumentException("No file uploaded");
                }

                tempFile = Files.createTempFile("schedule-upload-", ".csv");
                try (InputStream in = filePart.getInputStream()) {
                    Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                category.validate(tempFile);

                Path target = category.resolveForFilename(filename);
                if (Files.exists(target)) {
                    throw new IllegalArgumentException("A schedule with that filename already exists");
                }
                Files.createDirectories(target.getParent());
                Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

                try {
                    TransitSystem refreshed = buildTransitSystem();
                    systemRef.set(refreshed);
                } catch (IOException reloadEx) {
                    Files.deleteIfExists(target);
                    throw reloadEx;
                }

                return gson.toJson(Map.of(
                        "status", "ok",
                        "message", "Schedule added",
                        "path", relativePath(target)
                ));
            } catch (IllegalArgumentException iae) {
                res.status(400);
                return gson.toJson(Map.of("error", iae.getMessage()));
            } catch (Exception ex) {
                res.status(500);
                return gson.toJson(Map.of("error", ex.getMessage()));
            } finally {
                cleanupPart(filePart);
                deleteIfExists(tempFile);
            }
        });

        post("/admin/schedules/update", (req, res) -> {
            res.type("application/json");
            MultipartConfigElement config = new MultipartConfigElement(System.getProperty("java.io.tmpdir"));
            req.attribute(MULTIPART_CONFIG_ATTR, config);

            Part filePart = null;
            Path tempUpload = null;
            Path backup = null;
            try {
                String pathParam = optionalParam(req, "path")
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .orElseThrow(() -> new IllegalArgumentException("Missing schedule path"));

                ScheduleCategory category = ScheduleCategory.fromRelative(pathParam)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown schedule category"));

                Path target = category.resolveRelative(pathParam);
                if (!Files.exists(target)) {
                    throw new IllegalArgumentException("Schedule not found: " + pathParam);
                }

                filePart = req.raw().getPart("file");
                if (filePart == null || filePart.getSize() == 0) {
                    throw new IllegalArgumentException("No file uploaded");
                }

                tempUpload = Files.createTempFile("schedule-update-", ".csv");
                try (InputStream in = filePart.getInputStream()) {
                    Files.copy(in, tempUpload, StandardCopyOption.REPLACE_EXISTING);
                }

                category.validate(tempUpload);

                backup = Files.createTempFile("schedule-backup-", ".csv");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(tempUpload, target, StandardCopyOption.REPLACE_EXISTING);

                try {
                    TransitSystem refreshed = buildTransitSystem();
                    systemRef.set(refreshed);
                } catch (IOException reloadEx) {
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                    throw reloadEx;
                }

                return gson.toJson(Map.of("status", "ok", "message", "Schedule updated"));
            } catch (IllegalArgumentException iae) {
                res.status(400);
                return gson.toJson(Map.of("error", iae.getMessage()));
            } catch (Exception ex) {
                res.status(500);
                return gson.toJson(Map.of("error", ex.getMessage()));
            } finally {
                cleanupPart(filePart);
                deleteIfExists(tempUpload);
                deleteIfExists(backup);
            }
        });

        post("/admin/schedules/delete", (req, res) -> {
            res.type("application/json");
            try {
                Map<String, Object> payload = isJsonRequest(req) ? parseJsonObject(req.body()) : Map.of();
                String pathParam = resolvePathParam(req, payload)
                        .orElseThrow(() -> new IllegalArgumentException("Missing schedule path"));

                ScheduleCategory category = ScheduleCategory.fromRelative(pathParam)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown schedule category"));

                Path target = category.resolveRelative(pathParam);
                if (!Files.exists(target)) {
                    throw new IllegalArgumentException("Schedule not found: " + pathParam);
                }

                Files.createDirectories(TRASH_DIR);
                DeletedScheduleEntry entry = DeletedScheduleEntry.create(pathParam, category, target);
                Files.copy(target, entry.storagePath(), StandardCopyOption.REPLACE_EXISTING);

                Path backup = Files.createTempFile("schedule-delete-", ".csv");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(target);

                try {
                    TransitSystem refreshed = buildTransitSystem();
                    systemRef.set(refreshed);
                    recordDeletion(entry);
                } catch (IOException reloadEx) {
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                    deleteIfExists(entry.storagePath());
                    throw reloadEx;
                } finally {
                    deleteIfExists(backup);
                }

                return gson.toJson(Map.of("status", "ok", "message", "Schedule deleted"));
            } catch (IllegalArgumentException iae) {
                res.status(400);
                return gson.toJson(Map.of("error", iae.getMessage()));
            } catch (Exception ex) {
                res.status(500);
                return gson.toJson(Map.of("error", ex.getMessage()));
            }
        });

        post("/admin/schedules/restore", (req, res) -> {
            res.type("application/json");
            try {
                Map<String, Object> payload = isJsonRequest(req) ? parseJsonObject(req.body()) : Map.of();
                String idParam = resolveParam(req, payload, "id")
                        .orElseThrow(() -> new IllegalArgumentException("Missing bin id"));

                List<DeletedScheduleEntry> entries = loadTrashEntries();
                DeletedScheduleEntry match = null;
                for (DeletedScheduleEntry entry : entries) {
                    if (entry != null && idParam.equals(entry.id)) {
                        match = entry;
                        break;
                    }
                }

                if (match == null) {
                    throw new IllegalArgumentException("Deleted schedule not found");
                }

                Path storage = match.storagePath();
                if (!Files.exists(storage)) {
                    entries.removeIf(e -> e != null && idParam.equals(e.id));
                    saveTrashEntries(entries);
                    throw new IllegalArgumentException("Backup data missing for deleted schedule");
                }

                ScheduleCategory category = ScheduleCategory.fromRelative(match.relativePath)
                        .orElseThrow(() -> new IllegalArgumentException("Original schedule category unavailable"));

                Path restoreTarget = category.resolveRelative(match.relativePath);
                if (Files.exists(restoreTarget)) {
                    throw new IllegalArgumentException("A schedule already exists at " + match.relativePath);
                }

                Files.createDirectories(restoreTarget.getParent());
                Files.copy(storage, restoreTarget, StandardCopyOption.REPLACE_EXISTING);

                try {
                    TransitSystem refreshed = buildTransitSystem();
                    systemRef.set(refreshed);
                } catch (IOException reloadEx) {
                    deleteIfExists(restoreTarget);
                    throw reloadEx;
                }

                entries.removeIf(e -> e != null && idParam.equals(e.id));
                saveTrashEntries(entries);
                deleteIfExists(storage);

                return gson.toJson(Map.of("status", "ok", "message", "Schedule restored", "path", match.relativePath));
            } catch (IllegalArgumentException iae) {
                res.status(400);
                return gson.toJson(Map.of("error", iae.getMessage()));
            } catch (Exception ex) {
                res.status(500);
                return gson.toJson(Map.of("error", ex.getMessage()));
            }
        });

        post("/admin/schedules/reload", (req, res) -> {
            res.type("application/json");
            try {
                TransitSystem refreshed = buildTransitSystem();
                systemRef.set(refreshed);
                return gson.toJson(Map.of("status", "ok", "message", "Transit data reloaded"));
            } catch (IOException io) {
                res.status(500);
                return gson.toJson(Map.of("error", "Reload failed: " + io.getMessage()));
            }
        });


        try {
            init();
            awaitInitialization();
            System.out.println("JourneyAPI listening on port " + port);
            awaitStop();
        } catch (Exception e) {
            System.err.println("[ERROR] Spark startup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Constructs a TransitSystem instance by loading schedule data and walking edges. */

    private static TransitSystem buildTransitSystem() throws IOException {
        return new TransitSystem();
    }

    /** Creates data directories and trash storage if they are missing. */

    private static void ensureBaseDirectories() throws IOException {
        for (ScheduleCategory category : ScheduleCategory.values()) {
            Files.createDirectories(category.baseDirectory);
        }
    }

    /** Parses a JSON body into a map when the request is tagged as JSON. */

    private static Map<String, Object> parseJsonObject(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(body, type);
    }

    private static boolean isJsonRequest(Request req) {
        String contentType = req.contentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json");
    }

    /** Fetches a mandatory request parameter or throws a descriptive error. */

    private static Optional<String> resolveParam(Request req, Map<String, Object> payload, String key) {
        Optional<String> fromParam = optionalParam(req, key)
                .map(String::trim)
                .filter(s -> !s.isEmpty());
        if (fromParam.isPresent()) {
            return fromParam;
        }
        if (payload != null) {
            Object value = payload.get(key);
            if (value != null) {
                String trimmed = value.toString().trim();
                if (!trimmed.isEmpty()) {
                    return Optional.of(trimmed);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolvePathParam(Request req, Map<String, Object> payload) {
        return resolveParam(req, payload, "path");
    }

    private static String trim(String s) {
        return (s == null) ? null : s.trim();
    }

    /** Returns an optional request parameter trimmed of whitespace. */

    private static Optional<String> optionalParam(Request req, String name) {
        String value = req.raw().getParameter(name);
        if (value == null) {
            value = req.queryParams(name);
        }
        return Optional.ofNullable(value);
    }

    /** Guards against path traversal by cleaning user-supplied filenames. */

    private static String sanitizeFilename(String filename) {
        String sanitized = filename.replace("\\", "/");
        if (sanitized.contains("/")) {
            throw new IllegalArgumentException("Filename must not contain path separators");
        }
        if (sanitized.contains("..")) {
            throw new IllegalArgumentException("Filename must not contain '..'");
        }
        return sanitized;
    }

    private static String relativePath(Path file) {
        return DATA_ROOT.relativize(file).toString().replace('\\', '/');
    }

    /** Scans schedule directories to produce metadata for the admin portal. */

    private static List<Map<String, Object>> collectScheduleMetadata() throws IOException {
        List<Map<String, Object>> metadata = new ArrayList<>();
        for (ScheduleCategory category : ScheduleCategory.values()) {
            if (!Files.exists(category.baseDirectory)) {
                continue;
            }
            try (var listing = Files.list(category.baseDirectory)) {
                listing.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .sorted()
                        .forEach(path -> metadata.add(toDescriptor(category, path)));
            }
        }
        metadata.sort(Comparator.comparing((Map<String, Object> item) -> (String) item.get("type"))
                .thenComparing(item -> (String) item.get("name")));
        return metadata;
    }

    private static Map<String, Object> toDescriptor(ScheduleCategory category, Path file) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", category.id);
        map.put("typeLabel", category.label);
        map.put("name", file.getFileName().toString());
        map.put("path", relativePath(file));
        try {
            map.put("sizeBytes", Files.size(file));
            map.put("lastModified", FILE_TIME_FORMATTER.format(Files.getLastModifiedTime(file).toInstant()));
        } catch (IOException e) {
            map.put("sizeBytes", 0L);
            map.put("lastModified", FILE_TIME_FORMATTER.format(Instant.EPOCH));
        }
        return map;
    }

    /** Describes the supported schedule categories for uploads. */

    private static List<Map<String, Object>> scheduleTypeDefinitions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ScheduleCategory category : ScheduleCategory.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", category.id);
            entry.put("label", category.label);
            entry.put("description", category.description);
            entry.put("requiresFilename", true);
            list.add(entry);
        }
        return list;
    }

    /** Persists metadata describing a deleted schedule for potential restoration. */

    private static void recordDeletion(DeletedScheduleEntry entry) throws IOException {
        if (entry == null) {
            return;
        }
        List<DeletedScheduleEntry> entries = loadTrashEntries();
        entries.removeIf(existing -> existing == null || !existing.isValid() || entry.id.equals(existing.id) || !existing.hasStorage());
        entries.add(0, entry);
        pruneTrashEntries(entries);
        saveTrashEntries(entries);
    }

    /** Reads the trash metadata and returns recent deletions for display. */

    private static List<Map<String, Object>> recentDeletedSchedules() throws IOException {
        List<DeletedScheduleEntry> entries = loadTrashEntries();
        List<DeletedScheduleEntry> kept = new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (DeletedScheduleEntry entry : entries) {
            if (entry == null || !entry.isValid() || !entry.hasStorage()) {
                continue;
            }
            Optional<ScheduleCategory> category = ScheduleCategory.fromRelative(entry.relativePath);
            if (category.isEmpty()) {
                continue;
            }
            if (kept.size() < TRASH_LIMIT) {
                kept.add(entry);
                result.add(entry.toMap(category.get()));
            } else {
                deleteIfExists(entry.storagePath());
            }
        }
        if (kept.size() != entries.size()) {
            saveTrashEntries(kept);
        }
        return result;
    }

    /** Loads the trash metadata list from disk, gracefully handling missing files. */

    private static List<DeletedScheduleEntry> loadTrashEntries() throws IOException {
        if (!Files.exists(TRASH_METADATA)) {
            return new ArrayList<>();
        }
        try (BufferedReader reader = Files.newBufferedReader(TRASH_METADATA, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<DeletedScheduleEntry>>() {}.getType();
            List<DeletedScheduleEntry> entries = gson.fromJson(reader, listType);
            if (entries == null) {
                return new ArrayList<>();
            }
            entries.removeIf(entry -> entry == null || !entry.isValid());
            return new ArrayList<>(entries);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    /** Writes the trash metadata list back to disk. */

    private static void saveTrashEntries(List<DeletedScheduleEntry> entries) throws IOException {
        Files.createDirectories(TRASH_DIR);
        try (BufferedWriter writer = Files.newBufferedWriter(TRASH_METADATA, StandardCharsets.UTF_8)) {
            gson.toJson(entries, writer);
        }
    }

    /** Ensures the trash metadata does not exceed the configured retention limit. */

    private static void pruneTrashEntries(List<DeletedScheduleEntry> entries) {
        if (entries == null) {
            return;
        }
        while (entries.size() > TRASH_LIMIT) {
            DeletedScheduleEntry removed = entries.remove(entries.size() - 1);
            if (removed != null) {
                deleteIfExists(removed.storagePath());
            }
        }
    }

    private static final class DeletedScheduleEntry {
        String id;
        String relativePath;
        String categoryId;
        String filename;
        long sizeBytes;
        String deletedAtIso;
        String storageFilename;

        static DeletedScheduleEntry create(String relativePath, ScheduleCategory category, Path source) throws IOException {
            DeletedScheduleEntry entry = new DeletedScheduleEntry();
            Instant now = Instant.now();
            entry.id = now.toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
            entry.relativePath = relativePath;
            entry.categoryId = category.id;
            entry.filename = source.getFileName().toString();
            entry.sizeBytes = Files.size(source);
            entry.deletedAtIso = now.toString();
            String extension = "";
            String name = entry.filename;
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                extension = name.substring(dot);
            } else {
                extension = ".csv";
            }
            entry.storageFilename = entry.id + extension;
            return entry;
        }

        boolean hasStorage() {
            if (storageFilename == null || storageFilename.isBlank()) {
                return false;
            }
            return Files.exists(storagePath());
        }

        Path storagePath() {
            String filename = storageFilename;
            if (filename == null || filename.isBlank()) {
                filename = id != null ? id + ".csv" : UUID.randomUUID().toString() + ".csv";
            }
            return TRASH_DIR.resolve(filename);
        }

        boolean isValid() {
            return id != null && !id.isBlank()
                    && relativePath != null && !relativePath.isBlank()
                    && categoryId != null && !categoryId.isBlank()
                    && storageFilename != null && !storageFilename.isBlank();
        }

        Map<String, Object> toMap(ScheduleCategory category) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("path", relativePath);
            map.put("filename", filename);
            map.put("categoryId", category.id);
            map.put("categoryLabel", category.label);
            map.put("sizeBytes", sizeBytes);
            map.put("deletedAtIso", deletedAtIso);
            map.put("deletedAt", formattedDeletedAt());
            return map;
        }

        private String formattedDeletedAt() {
            try {
                return FILE_TIME_FORMATTER.format(Instant.parse(deletedAtIso));
            } catch (Exception ex) {
                return deletedAtIso != null ? deletedAtIso : "Unknown";
            }
        }
    }

    /** Safely disposes of multipart upload parts to free resources. */

    private static void cleanupPart(Part part) {
        if (part != null) {
            try {
                part.delete();
            } catch (Exception ignored) {
            }
        }
    }

    /** Deletes a file if it exists, suppressing IO errors. */

    private static void deleteIfExists(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    /** Groups raw PathStep output into user-facing legs with timing and stop lists. */

    private static List<Map<String, Object>> buildLegs(List<PathStep> steps, DataLoader loader) {
        List<Map<String, Object>> legs = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            return legs;
        }

        Map<String, Object> currentLeg = null;
        List<Map<String, Object>> currentStops = new ArrayList<>();

        for (PathStep step : steps) {
            if (currentLeg == null || (step.getTripID() != null && !step.getTripID().equals(currentLeg.get("tripId")))) {
                if (currentLeg != null) {
                    finalizeLeg(currentLeg);
                    currentLeg.put("stops", currentStops);
                    legs.add(currentLeg);
                    currentStops = new ArrayList<>();
                }
                currentLeg = new LinkedHashMap<>();
                String tripId = step.getTripID();
                currentLeg.put("tripId", tripId);
                currentLeg.put("mode", resolveMode(step));
                currentLeg.put("operator", deriveOperator(tripId, loader));
                currentLeg.put("line", deriveLine(tripId));
                currentLeg.put("startTime", step.getTime());
                currentLeg.put("startStop", step.getStopName());
                currentLeg.put("startCoords", coordsFor(step, loader));
                currentLeg.put("distanceKm", step.isWalking() ? step.getDistanceKm() : 0.0);
            } else if (step.isWalking()) {
                double existing = ((Number) currentLeg.getOrDefault("distanceKm", 0.0)).doubleValue();
                if (step.getDistanceKm() > existing) {
                    currentLeg.put("distanceKm", step.getDistanceKm());
                }
            }

            Map<String, Object> stopEntry = new LinkedHashMap<>();
            stopEntry.put("time", step.getTime());
            stopEntry.put("stop", step.getStopName());
            stopEntry.put("coords", coordsFor(step, loader));
            currentStops.add(stopEntry);

            currentLeg.put("endTime", step.getTime());
            currentLeg.put("endStop", step.getStopName());
            currentLeg.put("endCoords", coordsFor(step, loader));
        }

        if (currentLeg != null) {
            finalizeLeg(currentLeg);
            currentLeg.put("stops", currentStops);
            legs.add(currentLeg);
        }

        return legs;
    }

    /** Adds calculated duration metadata before a leg is returned. */

    private static void finalizeLeg(Map<String, Object> leg) {
        String start = (String) leg.get("startTime");
        String end = (String) leg.get("endTime");
        Integer duration = minutesBetween(start, end);
        if (duration != null) {
            leg.put("durationMinutes", duration);
        }
    }

    /** Returns the minute difference between two HH:MM timestamps if valid. */

    private static Integer minutesBetween(String start, String end) {
        Integer startMin = timeToMinutes(start);
        Integer endMin = timeToMinutes(end);
        if (startMin == null || endMin == null || endMin < startMin) return null;
        return endMin - startMin;
    }

    /** Determines the human-readable mode label for a path step. */

    private static String resolveMode(PathStep step) {
        if (step == null) return "UNKNOWN";
        if (step.isWalking()) return "WALK";
        String mode = step.getMode();
        if (mode == null) return "UNKNOWN";
        mode = mode.toUpperCase(Locale.ROOT);
        if (mode.equals("BUS") || mode.equals("TRAIN")) return mode;
        return "UNKNOWN";
    }

    private static String deriveLine(String tripId) {
        if (tripId == null) return "";
        if (tripId.startsWith("BUS_")) {
            String[] parts = tripId.split("_");
            if (parts.length >= 2) return parts[1];
        }
        if (tripId.startsWith("TRAIN")) {
            return "TRAIN";
        }
        return tripId;
    }

    /**
     * Attempts to resolve a human-friendly operator label for the given trip.
     * Prefers loader-provided bus operators and falls back to Metrorail for trains.
     */
    private static String deriveOperator(String tripId, DataLoader loader) {
        if (tripId == null || loader == null) return null;
        Trip trip = loader.trips.get(tripId);
        if (trip == null) return null;
        String operator = loader.getRouteOperator(trip.getRoute());
        if (operator != null && !operator.isBlank()) {
            return operator;
        }
        String mode = trip.getMode();
        if (mode != null && mode.equalsIgnoreCase("TRAIN")) {
            return "METRORAIL";
        }
        return null;
    }

    /** Extracts a latitude/longitude pair for a step using cached stop details. */

    private static Map<String, Double> coordsFor(PathStep step, DataLoader loader) {
        if (step == null) return Map.of();
        StopLocation loc = loader.stopDetails.get(step.getStopID());
        if (loc != null) {
            return Map.of("lat", loc.getLat(), "lon", loc.getLon());
        }
        double lat = step.getLat();
        double lon = step.getLon();
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            return Map.of();
        }
        if (Math.abs(lat) < 1e-9 && Math.abs(lon) < 1e-9) {
            return Map.of();
        }
        return Map.of("lat", lat, "lon", lon);
    }

    /** Builds a high-level summary map for a completed journey. */

    private static Map<String, Object> computeSummary(List<PathStep> steps, String dayType) {
        if (steps.isEmpty()) return Map.of();
        String firstT = steps.get(0).getTime();
        String lastT  = steps.get(steps.size() - 1).getTime();
        int duration = timeToMinutes(lastT) - timeToMinutes(firstT);

        int transfers = 0;
        String prevTrip = steps.get(0).getTripID();
        for (int i = 1; i < steps.size(); i++) {
            String currTrip = steps.get(i).getTripID();
            if (!Objects.equals(prevTrip, currTrip)) {
                if (!isWalkTrip(prevTrip) && !isWalkTrip(currTrip)) {
                    transfers++;
                }
            }
            prevTrip = currTrip;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("start", firstT);
        summary.put("end", lastT);
        summary.put("durationMinutes", duration);
        summary.put("transfers", transfers);
        if (dayType != null && !dayType.isBlank()) {
            summary.put("dayType", dayType);
        }
        return summary;
    }

    private static boolean isWalkTrip(String tripId) {
        return tripId != null && tripId.equalsIgnoreCase("WALK");
    }

    private static int timeToMinutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Configures CORS headers so a local static frontend can call the API. */

    private static void enableCORS() {
        options("/*", (request, response) -> {
            String requestHeaders = request.headers("Access-Control-Request-Headers");
            if (requestHeaders != null) {
                response.header("Access-Control-Allow-Headers", requestHeaders);
            } else {
                response.header("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization");
            }

            String requestMethod = request.headers("Access-Control-Request-Method");
            if (requestMethod != null) {
                response.header("Access-Control-Allow-Methods", requestMethod);
            } else {
                response.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            }

            String origin = request.headers("Origin");
            String allowOrigin = resolveAllowedOrigin(origin);
            response.header("Access-Control-Allow-Origin", allowOrigin);
            if (allowsCredentials(allowOrigin)) {
                response.header("Access-Control-Allow-Credentials", "true");
            }
            response.status(200);
            return "OK";
        });

        before((request, response) -> {
            String origin = request.headers("Origin");
            String allowOrigin = resolveAllowedOrigin(origin);
            response.header("Access-Control-Allow-Origin", allowOrigin);
            if (allowsCredentials(allowOrigin)) {
                response.header("Access-Control-Allow-Credentials", "true");
            }
            response.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            response.header("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization");
        });
    }

    /** Returns an allowed origin for CORS, echoing localhost/127.0.0.1 if provided, otherwise '*'. */
    private static String resolveAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return "*";
        }
        String lower = origin.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://localhost") || lower.startsWith("https://localhost")
                || lower.startsWith("http://127.0.0.1") || lower.startsWith("https://127.0.0.1")) {
            return origin;
        }
        // Default to '*' for non-local origins
        return "*";
    }

    /** Indicates whether we should include the credentials header for the given allow-origin value. */
    private static boolean allowsCredentials(String allowOrigin) {
        // Credentials cannot be used with '*'. Only enable when echoing a specific localhost origin.
        return allowOrigin != null && !"*".equals(allowOrigin);
    }

    private enum ScheduleCategory {
        MYCITI_BUS("MYCITI_BUS", "MyCiTi Bus", "MyCiTi bus schedules", "myciti-bus-schedules") {
            @Override
            void validate(Path file) throws IOException {
                validateHeader(file, new String[]{"route_number", "day_type"}, "MyCiTi schedule must start with route_number,day_type");
            }
        },
        GOLDEN_ARROW_BUS("GOLDEN_ARROW_BUS", "Golden Arrow Bus", "Golden Arrow bus schedules", "ga-bus-schedules") {
            @Override
            void validate(Path file) throws IOException {
                validateHeader(file, new String[]{"day_type"}, "Golden Arrow schedule must start with day_type");
            }
        },
        TRAIN("TRAIN", "Train", "Train schedules", "train-schedules-2014") {
            @Override
            void validate(Path file) throws IOException {
                validateHeader(file, new String[]{"trip_id", "day_type", "direction", "route"}, "Train schedule must start with trip_id,day_type,direction,route");
            }
        };

        final String id;
        final String label;
        final String description;
        final Path baseDirectory;
        private final String folder;

        ScheduleCategory(String id, String label, String description, String folder) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.folder = folder;
            this.baseDirectory = DATA_ROOT.resolve(folder).normalize();
        }

        static Optional<ScheduleCategory> fromId(String id) {
            for (ScheduleCategory category : values()) {
                if (category.id.equalsIgnoreCase(id)) {
                    return Optional.of(category);
                }
            }
            return Optional.empty();
        }

        static Optional<ScheduleCategory> fromRelative(String relativePath) {
            Path normalized = DATA_ROOT.resolve(normalizeRelative(relativePath)).normalize();
            for (ScheduleCategory category : values()) {
                if (normalized.startsWith(category.baseDirectory)) {
                    return Optional.of(category);
                }
            }
            return Optional.empty();
        }

        Path resolveForFilename(String filename) {
            return baseDirectory.resolve(filename).normalize();
        }

        Path resolveRelative(String relativePath) {
            Path resolved = DATA_ROOT.resolve(normalizeRelative(relativePath)).normalize();
            if (!resolved.startsWith(baseDirectory)) {
                throw new IllegalArgumentException("Path is outside the allowed directory");
            }
            return resolved;
        }

        abstract void validate(Path file) throws IOException;

        private static String normalizeRelative(String input) {
            String cleaned = Optional.ofNullable(input).orElse("").replace('\\', '/');
            while (cleaned.startsWith("/")) {
                cleaned = cleaned.substring(1);
            }
            return cleaned;
        }

        static void validateHeader(Path file, String[] requiredColumns, String errorMessage) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String header = readNonEmptyLine(reader);
                if (header == null) {
                    throw new IllegalArgumentException("CSV file is empty");
                }
                String[] columns = header.split(",", -1);
                if (columns.length < requiredColumns.length) {
                    throw new IllegalArgumentException(errorMessage);
                }
                for (int i = 0; i < requiredColumns.length; i++) {
                    String expected = requiredColumns[i].toLowerCase(Locale.ROOT);
                    String actual = columns[i].trim().toLowerCase(Locale.ROOT);
                    if (!actual.equals(expected)) {
                        throw new IllegalArgumentException(errorMessage);
                    }
                }
                if (!hasDataRow(reader)) {
                    throw new IllegalArgumentException("CSV file contains no data rows");
                }
            }
        }

        private static String readNonEmptyLine(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    return line;
                }
            }
            return null;
        }

        private static boolean hasDataRow(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }
}





