package com.example.woshield;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transparent, deterministic, explainable rules-based intelligence engine
 * for Woshield journey and routine monitoring.
 *
 * Implements non-downgrading emergency hierarchy, GPS noise filtering,
 * sustained route-deviation confirmation, configurable delay grace periods,
 * and false-alarm deduplication.
 */
public class SafetyIntelligenceEngine {

    /**
     * Evaluates the current safety context against configured rules.
     *
     * @param context Current user movement, routine, and emergency state.
     * @param config Rule configuration thresholds and grace periods.
     * @param recentDecision Previous decision for deduplication and continuity.
     * @param currentTimestamp Evaluation timestamp in milliseconds.
     * @return Deterministic, explainable SafetyDecision.
     */
    @NonNull
    public SafetyDecision evaluate(@NonNull SafetyContext context,
                                   @NonNull SafetyRuleConfig config,
                                   @Nullable SafetyDecision recentDecision,
                                   long currentTimestamp) {

        String decisionId = UUID.randomUUID().toString();
        String ownerUid = context.getOwnerUid();
        String routineId = context.getRoutineId();
        List<SafetySignal> signals = new ArrayList<>();

        // =========================================================================
        // 1. RULE 1: EXPLICIT SOS & EMERGENCY ESCALATION (HIGHEST PRIORITY)
        // Explicit SOS is NEVER delayed, downgraded, or suppressed by other signals.
        // =========================================================================
        if (context.isExplicitSosActive()) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_EXPLICIT_SOS, 1.0, 1.0, true,
                    "Explicit SOS button or voice emergency alert is actively triggered."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_EMERGENCY_ACTIVE,
                    SafetyDecision.RULE_EXPLICIT_SOS,
                    "Emergency SOS is actively triggered. Immediate family and emergency escalation active."
            );
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            decision.setActionTaken(SafetyDecision.ACTION_SOS_ACTIVE);
            return decision;
        }

        if (context.isMissedCheckIn()) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_MISSED_CHECKIN, 1.0, 1.0, true,
                    "Smart Check-In deadline elapsed without confirmation."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_EMERGENCY_ACTIVE,
                    SafetyDecision.RULE_MISSED_CHECKIN,
                    "Smart Check-In deadline was missed without response. Automated emergency escalation activated."
            );
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            decision.setActionTaken(SafetyDecision.ACTION_SOS_ACTIVE);
            return decision;
        }

        // =========================================================================
        // 2. RULE 2: EXPLICIT ASSISTANCE REQUEST (WALK-WITH-ME / YELLOW ALERT)
        // =========================================================================
        if (context.isExplicitAssistanceActive()) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_EXPLICIT_ASSISTANCE, 1.0, 1.0, true,
                    "User initiated assistance or walk-with-me check-in."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_ASSISTANCE_REQUESTED,
                    SafetyDecision.RULE_EXPLICIT_ASSISTANCE,
                    "Assistance request is active. Trusted contacts have been notified to monitor progress."
            );
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            decision.setActionTaken(SafetyDecision.ACTION_ASSISTANCE_ACTIVE);
            return decision;
        }

        // =========================================================================
        // 3. RULE 3: LOCATION AVAILABILITY & NOISE FILTERING
        // Evaluates sensor uncertainty rather than making false danger claims.
        // =========================================================================
        if (!context.isLocationAvailable()) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_LOCATION_STALE, 0.0, 0.0, true,
                    "No location samples recorded yet."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_MONITORING_UNCERTAIN,
                    SafetyDecision.RULE_STALE_LOCATION,
                    "Location data is currently unavailable. GPS or network connectivity may be inactive."
            );
            decision.setUncertaintyReason("GPS_UNAVAILABLE");
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            return decision;
        }

        if (context.isLocationStale(config.getStaleLocationThresholdMs(), currentTimestamp)) {
            long ageSeconds = (currentTimestamp - context.getLocationTimestamp()) / 1000L;
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_LOCATION_STALE, ageSeconds,
                    config.getStaleLocationThresholdMs() / 1000.0, true,
                    "Latest location is " + (ageSeconds / 60) + " minutes old."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_MONITORING_UNCERTAIN,
                    SafetyDecision.RULE_STALE_LOCATION,
                    "We could not confirm your latest location. GPS or network availability may be affecting updates."
            );
            decision.setUncertaintyReason("GPS_STALE");
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            return decision;
        }

        if (!context.isLocationAccurate(config.getMaxAllowedGpsAccuracyMeters())) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_LOCATION_INACCURATE, context.getAccuracyMeters(),
                    config.getMaxAllowedGpsAccuracyMeters(), true,
                    "GPS accuracy is " + (int) context.getAccuracyMeters() + "m, exceeding allowed threshold."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_MONITORING_UNCERTAIN,
                    SafetyDecision.RULE_POOR_ACCURACY,
                    "GPS accuracy is currently low (" + (int) context.getAccuracyMeters() + "m radius). Waiting for satellite precision before analyzing corridor deviation."
            );
            decision.setUncertaintyReason("POOR_ACCURACY");
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            return decision;
        }

        // =========================================================================
        // 4. RULE 4: DESTINATION OR SAFE STOP REACHED
        // =========================================================================
        if (context.isHasArrivedAtDestination()) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_DESTINATION_ARRIVED, 0.0, 0.0, true,
                    "Arrived safely at destination."
            );
            signals.add(sig);

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_NO_ACTION,
                    SafetyDecision.RULE_DESTINATION_REACHED,
                    "You have arrived safely at your destination."
            );
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            return decision;
        }

        // =========================================================================
        // 5. RULE 5: SUSTAINED ROUTE DEVIATION
        // Requires sustained evidence (consecutive samples) and respects user-confirmed changes.
        // =========================================================================
        if (context.isUserConfirmedRouteChange()) {
            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_USER_ROUTE_CHANGE, 1.0, 1.0, true,
                    "User acknowledged alternate route or detour."
            );
            signals.add(sig);
            // Confirmed change suppresses deviation warnings
        } else if (context.getDistanceFromRouteMeters() >= config.getDeviationDistanceThresholdMeters()) {
            boolean isSustained = context.getSustainedDeviationSamples() >= config.getSustainedDeviationSamplesRequired();

            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_ROUTE_DEVIATION,
                    context.getDistanceFromRouteMeters(),
                    config.getDeviationDistanceThresholdMeters(),
                    isSustained,
                    "Deviation distance: " + (int) context.getDistanceFromRouteMeters() + "m across "
                            + context.getSustainedDeviationSamples() + " samples."
            );
            signals.add(sig);

            if (isSustained) {
                // Check false-alarm deduplication
                if (isDeduplicated(recentDecision, SafetyDecision.RULE_SUSTAINED_DEVIATION, config.getDeduplicationWindowMs(), currentTimestamp)) {
                    SafetyDecision dedupDecision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_NO_ACTION,
                    SafetyDecision.RULE_DEDUPLICATED,
                    "Route deviation already being handled; suppressing duplicate warning."
                    );
                    dedupDecision.setTimestamp(currentTimestamp);
                    dedupDecision.setSignals(signals);
                    dedupDecision.setDeduplicated(true);
                    return dedupDecision;
                }

                SafetyDecision decision = new SafetyDecision(
                        decisionId, ownerUid, routineId,
                        SafetyDecision.OUTCOME_SAFETY_CHECK_RECOMMENDED,
                        SafetyDecision.RULE_SUSTAINED_DEVIATION,
                        "Your location appears farther from the planned route than your configured threshold ("
                                + (int) context.getDistanceFromRouteMeters() + "m)."
                );
                decision.setTimestamp(currentTimestamp);
                decision.setSignals(signals);
                return decision;
            } else {
                // Brief single-sample jitter or turn; ignore until sustained
                SafetyDecision jitterDecision = new SafetyDecision(
                        decisionId, ownerUid, routineId,
                        SafetyDecision.OUTCOME_NO_ACTION,
                        SafetyDecision.RULE_NORMAL_TRANSIT,
                        "Brief deviation noted (" + (int) context.getDistanceFromRouteMeters() + "m). Waiting for sustained readings to filter GPS jitter."
                );
                jitterDecision.setTimestamp(currentTimestamp);
                jitterDecision.setSignals(signals);
                return jitterDecision;
            }
        }

        // =========================================================================
        // 6. RULE 6: UNEXPECTED DELAY (EXCEEDING CONFIGURED GRACE PERIOD)
        // =========================================================================
        long gracePeriodMs = config.getDelayGracePeriodMinutes() * 60 * 1000L;
        if (context.isDelayExceeded(gracePeriodMs, currentTimestamp)) {
            int delayMinutes = context.getDelayMinutes(currentTimestamp);

            SafetySignal sig = new SafetySignal(
                    SafetySignal.TYPE_UNEXPECTED_DELAY,
                    delayMinutes,
                    config.getDelayGracePeriodMinutes(),
                    true,
                    "Transit delayed by " + delayMinutes + " minutes past scheduled arrival."
            );
            signals.add(sig);

            // Check false-alarm deduplication
            if (isDeduplicated(recentDecision, SafetyDecision.RULE_UNEXPECTED_DELAY, config.getDeduplicationWindowMs(), currentTimestamp)) {
                SafetyDecision dedupDecision = new SafetyDecision(
                        decisionId, ownerUid, routineId,
                        SafetyDecision.OUTCOME_NO_ACTION,
                        SafetyDecision.RULE_DEDUPLICATED,
                        "Delay alert already active; suppressing duplicate warning."
                );
                dedupDecision.setTimestamp(currentTimestamp);
                dedupDecision.setSignals(signals);
                dedupDecision.setDeduplicated(true);
                return dedupDecision;
            }

            SafetyDecision decision = new SafetyDecision(
                    decisionId, ownerUid, routineId,
                    SafetyDecision.OUTCOME_SAFETY_CHECK_RECOMMENDED,
                    SafetyDecision.RULE_UNEXPECTED_DELAY,
                    "Your journey is taking longer than expected (" + delayMinutes + "m past scheduled arrival)."
            );
            decision.setTimestamp(currentTimestamp);
            decision.setSignals(signals);
            return decision;
        }

        // =========================================================================
        // 7. RULE 7: NORMAL TRANSIT
        // =========================================================================
        SafetyDecision decision = new SafetyDecision(
                decisionId, ownerUid, routineId,
                SafetyDecision.OUTCOME_NO_ACTION,
                SafetyDecision.RULE_NORMAL_TRANSIT,
                "Transit progressing normally along scheduled route."
        );
        decision.setTimestamp(currentTimestamp);
        decision.setSignals(signals);
        return decision;
    }

    /**
     * Checks if a safety alert is redundant within the configured deduplication window.
     */
    private boolean isDeduplicated(@Nullable SafetyDecision recent,
                                   @NonNull String rule,
                                   long dedupWindowMs,
                                   long nowMs) {
        if (recent == null) return false;
        if (recent.isDismissed()) return false;
        if (!rule.equals(recent.getTriggeringRule())) return false;
        return (nowMs - recent.getTimestamp()) < dedupWindowMs;
    }
}
