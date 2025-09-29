package backend;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class QueryPreferences {
    private static final Set<String> DEFAULT_TRANSIT_MODES = Set.of("BUS", "TRAIN");

    private final Set<String> allowedModes;
    private final double maxConsecutiveWalkKm;
    private final double maxCumulativeWalkKm;
    private final Double maxSingleWalkKm;
    private final boolean walkingAllowed;
    private final boolean preferenceSpecified;
    private final boolean valid;

    private QueryPreferences(Set<String> allowedModes,
                             double maxConsecutiveWalkKm,
                             double maxCumulativeWalkKm,
                             Double maxSingleWalkKm,
                             boolean walkingAllowed,
                             boolean preferenceSpecified,
                             boolean valid) {
        this.allowedModes = allowedModes;
        this.maxConsecutiveWalkKm = maxConsecutiveWalkKm;
        this.maxCumulativeWalkKm = maxCumulativeWalkKm;
        this.maxSingleWalkKm = maxSingleWalkKm;
        this.walkingAllowed = walkingAllowed;
        this.preferenceSpecified = preferenceSpecified;
        this.valid = valid;
    }

    public static QueryPreferences baseline(double defaultConsecutive, double defaultCumulative) {
        return new QueryPreferences(null, defaultConsecutive, defaultCumulative, null, true, false, true);
    }

    public static QueryPreferences fromRawInputs(String modesParam,
                                                 String maxWalkMetersParam,
                                                 double defaultConsecutive,
                                                 double defaultCumulative) {
        boolean valid = true;
        Set<String> modeFilter = null;
        boolean modeConstraint = false;

        if (modesParam != null && !modesParam.isBlank()) {
            LinkedHashSet<String> parsed = new LinkedHashSet<>();
            String[] tokens = modesParam.split(",");
            for (String token : tokens) {
                if (token == null) continue;
                String normalized = token.trim().toUpperCase(Locale.ROOT);
                if (normalized.isEmpty()) continue;
                parsed.add(normalized);
            }

            parsed.removeIf(mode -> !DEFAULT_TRANSIT_MODES.contains(mode));

            if (parsed.isEmpty()) {
                valid = false;
            } else if (parsed.containsAll(DEFAULT_TRANSIT_MODES) && parsed.size() == DEFAULT_TRANSIT_MODES.size()) {
                // No effective filter; treat as default behaviour.
                modeFilter = null;
            } else {
                modeFilter = Collections.unmodifiableSet(parsed);
                modeConstraint = true;
            }
        }

        boolean walkingAllowed = true;
        Double singleWalkKm = null;
        double consecutive = defaultConsecutive;
        double cumulative = defaultCumulative;
        boolean walkingConstraint = false;

        if (maxWalkMetersParam != null && !maxWalkMetersParam.isBlank()) {
            walkingConstraint = true;
            try {
                double meters = Double.parseDouble(maxWalkMetersParam.trim());
                if (meters <= 0.0) {
                    walkingAllowed = false;
                    consecutive = 0.0;
                    cumulative = 0.0;
                    singleWalkKm = 0.0;
                } else {
                    double km = meters / 1000.0;
                    singleWalkKm = km;
                    consecutive = Math.min(defaultConsecutive, km);
                    cumulative = Math.min(defaultCumulative, km);
                }
            } catch (NumberFormatException ex) {
                valid = false;
                walkingConstraint = false;
            }
        }

        boolean preferenceSpecified = modeConstraint || walkingConstraint;
        if (!walkingConstraint) {
            singleWalkKm = null;
        }

        return new QueryPreferences(modeFilter,
                consecutive,
                cumulative,
                singleWalkKm,
                walkingAllowed,
                preferenceSpecified,
                valid);
    }

    public boolean allowsMode(String mode) {
        if (allowedModes == null || allowedModes.isEmpty() || mode == null) return true;
        return allowedModes.contains(mode.toUpperCase(Locale.ROOT));
    }

    public Set<String> getAllowedModes() {
        return allowedModes == null ? DEFAULT_TRANSIT_MODES : allowedModes;
    }

    public double getMaxConsecutiveWalkKm() {
        return maxConsecutiveWalkKm;
    }

    public double getMaxCumulativeWalkKm() {
        return maxCumulativeWalkKm;
    }

    public Double getMaxSingleWalkKm() {
        return maxSingleWalkKm;
    }

    public boolean allowsWalking() {
        return walkingAllowed;
    }

    public boolean isPreferenceSpecified() {
        return preferenceSpecified;
    }

    public boolean requiresCSA() {
        return preferenceSpecified;
    }

    public boolean isValid() {
        return valid;
    }
}
