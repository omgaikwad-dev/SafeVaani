package com.example.woshield;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SafetyIntelligenceEngineTest {

    private SafetyIntelligenceEngine engine;
    private SafetyRuleConfig config;
    private long baseTime;

    @Before
    public void setUp() {
        engine = new SafetyIntelligenceEngine();
        config = SafetyRuleConfig.defaultConfig();
        baseTime = 1700000000000L; // Fixed test reference timestamp
    }

    private SafetyContext createNormalContext() {
        SafetyContext context = new SafetyContext();
        context.setOwnerUid("user_123");
        context.setRoutineId("routine_work");
        context.setRoutineTitle("Evening Commute");
        context.setLocationAvailable(true);
        context.setLatitude(37.7749);
        context.setLongitude(-122.4194);
        context.setAccuracyMeters(15.0f); // Good GPS accuracy
        context.setLocationTimestamp(baseTime); // Fresh timestamp
        context.setJourneyStartMillis(baseTime - (20 * 60 * 1000L));
        context.setExpectedArrivalMillis(baseTime + (15 * 60 * 1000L)); // Arrival in 15 mins
        context.setDistanceFromRouteMeters(20.0); // Within corridor
        context.setSustainedDeviationSamples(0);
        return context;
    }

    @Test
    public void testNormalTransit_producesNoAction() {
        SafetyContext context = createNormalContext();
        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertNotNull(decision);
        assertEquals(SafetyDecision.OUTCOME_NO_ACTION, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_NORMAL_TRANSIT, decision.getTriggeringRule());
        assertTrue(decision.getExplanation().contains("Transit progressing normally"));
    }

    @Test
    public void testExplicitSos_neverSuppressedOrDowngraded() {
        SafetyContext context = createNormalContext();
        context.setExplicitSosActive(true); // Active SOS button

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_EMERGENCY_ACTIVE, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_EXPLICIT_SOS, decision.getTriggeringRule());
        assertEquals(SafetyDecision.ACTION_SOS_ACTIVE, decision.getActionTaken());
    }

    @Test
    public void testExplicitAssistance_preservesAssistanceOutcome() {
        SafetyContext context = createNormalContext();
        context.setExplicitAssistanceActive(true);

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_ASSISTANCE_REQUESTED, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_EXPLICIT_ASSISTANCE, decision.getTriggeringRule());
        assertEquals(SafetyDecision.ACTION_ASSISTANCE_ACTIVE, decision.getActionTaken());
    }

    @Test
    public void testMissedCheckIn_escalatesToEmergencyActive() {
        SafetyContext context = createNormalContext();
        context.setMissedCheckIn(true);

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_EMERGENCY_ACTIVE, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_MISSED_CHECKIN, decision.getTriggeringRule());
    }

    @Test
    public void testUnavailableLocation_reportsUncertaintyNotDanger() {
        SafetyContext context = createNormalContext();
        context.setLocationAvailable(false); // GPS lost or turned off

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_MONITORING_UNCERTAIN, decision.getOutcome());
        assertEquals("GPS_UNAVAILABLE", decision.getUncertaintyReason());
        assertFalse("Must not declare emergency for missing location",
                SafetyDecision.OUTCOME_EMERGENCY_ACTIVE.equals(decision.getOutcome()));
    }

    @Test
    public void testStaleLocation_reportsUncertainty() {
        SafetyContext context = createNormalContext();
        // Location is 10 minutes old (> 5 minute threshold)
        context.setLocationTimestamp(baseTime - (10 * 60 * 1000L));

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_MONITORING_UNCERTAIN, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_STALE_LOCATION, decision.getTriggeringRule());
        assertEquals("GPS_STALE", decision.getUncertaintyReason());
    }

    @Test
    public void testPoorGpsAccuracy_filtersJitterAndAwaitsPrecision() {
        SafetyContext context = createNormalContext();
        context.setAccuracyMeters(120.0f); // 120m accuracy is worse than 65m threshold
        context.setDistanceFromRouteMeters(350.0); // Would look like deviation if inaccurate GPS wasn't filtered!

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_MONITORING_UNCERTAIN, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_POOR_ACCURACY, decision.getTriggeringRule());
        assertEquals("POOR_ACCURACY", decision.getUncertaintyReason());
    }

    @Test
    public void testBriefNoisyDeviation_filteredUntilSustained() {
        SafetyContext context = createNormalContext();
        context.setDistanceFromRouteMeters(300.0); // Beyond 250m threshold
        context.setSustainedDeviationSamples(1); // Only 1 sample (need 3)

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        // Should NOT trigger check-in on a single noisy spike
        assertEquals(SafetyDecision.OUTCOME_NO_ACTION, decision.getOutcome());
        assertTrue(decision.getExplanation().contains("Waiting for sustained readings"));
    }

    @Test
    public void testSustainedDeviation_triggersSafetyCheck() {
        SafetyContext context = createNormalContext();
        context.setDistanceFromRouteMeters(320.0); // Beyond 250m
        context.setSustainedDeviationSamples(3); // 3 consecutive samples

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_SAFETY_CHECK_RECOMMENDED, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_SUSTAINED_DEVIATION, decision.getTriggeringRule());
        assertTrue(decision.getExplanation().contains("farther from the planned route"));
    }

    @Test
    public void testUserConfirmedRouteChange_suppressesDeviationWarning() {
        SafetyContext context = createNormalContext();
        context.setDistanceFromRouteMeters(450.0);
        context.setSustainedDeviationSamples(5);
        context.setUserConfirmedRouteChange(true); // User intentionally detoured

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        // Deviation should be suppressed because user acknowledged detour
        assertEquals(SafetyDecision.OUTCOME_NO_ACTION, decision.getOutcome());
    }

    @Test
    public void testDelayWithinGracePeriod_isIgnored() {
        SafetyContext context = createNormalContext();
        context.setExpectedArrivalMillis(baseTime - (5 * 60 * 1000L)); // 5 mins late
        // Config grace period is 10 mins

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_NO_ACTION, decision.getOutcome());
    }

    @Test
    public void testDelayExceedingGracePeriod_triggersSafetyCheck() {
        SafetyContext context = createNormalContext();
        context.setExpectedArrivalMillis(baseTime - (15 * 60 * 1000L)); // 15 mins late (> 10m grace)

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_SAFETY_CHECK_RECOMMENDED, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_UNEXPECTED_DELAY, decision.getTriggeringRule());
        assertTrue(decision.getExplanation().contains("longer than expected"));
    }

    @Test
    public void testDeduplication_suppressesRepeatedPrompts() {
        SafetyContext context = createNormalContext();
        context.setExpectedArrivalMillis(baseTime - (20 * 60 * 1000L)); // 20 mins late

        // First evaluation triggers safety check
        SafetyDecision firstDecision = engine.evaluate(context, config, null, baseTime);
        assertEquals(SafetyDecision.OUTCOME_SAFETY_CHECK_RECOMMENDED, firstDecision.getOutcome());

        // Second evaluation 2 minutes later for same delay
        long timeTwoMinutesLater = baseTime + (2 * 60 * 1000L);
        SafetyDecision secondDecision = engine.evaluate(context, config, firstDecision, timeTwoMinutesLater);

        // Deduplication must suppress second prompt
        assertEquals(SafetyDecision.OUTCOME_NO_ACTION, secondDecision.getOutcome());
        assertTrue(secondDecision.isDeduplicated());
        assertEquals(SafetyDecision.RULE_DEDUPLICATED, secondDecision.getTriggeringRule());
    }

    @Test
    public void testSafelyArrivedAtDestination_producesNoAction() {
        SafetyContext context = createNormalContext();
        context.setHasArrivedAtDestination(true);
        context.setExpectedArrivalMillis(baseTime - (30 * 60 * 1000L)); // Even if clock passed arrival

        SafetyDecision decision = engine.evaluate(context, config, null, baseTime);

        assertEquals(SafetyDecision.OUTCOME_NO_ACTION, decision.getOutcome());
        assertEquals(SafetyDecision.RULE_DESTINATION_REACHED, decision.getTriggeringRule());
        assertTrue(decision.getExplanation().contains("arrived safely"));
    }
}
