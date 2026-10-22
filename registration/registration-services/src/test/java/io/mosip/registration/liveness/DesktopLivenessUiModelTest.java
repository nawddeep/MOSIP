package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import io.mosip.registration.liveness.DesktopLivenessUiModel.Actions;
import io.mosip.registration.liveness.DesktopLivenessUiModel.ChallengeCounter;
import io.mosip.registration.liveness.DesktopLivenessUiModel.Icon;
import io.mosip.registration.liveness.DesktopLivenessUiModel.Ring;
import io.mosip.registration.liveness.DesktopLivenessUiModel.View;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.Workflow;

public class DesktopLivenessUiModelTest {

	private static final String[] LANGS = { "en", "fr", "ar" };
	private static final Map<String, Properties> LABELS = new HashMap<>();

	@BeforeClass
	public static void loadBundles() throws Exception {
		for (String lang : LANGS) {
			File f = new File("../registration-client/src/main/resources/labels_" + lang + ".properties");
			assertTrue(f.getAbsolutePath(), f.isFile());
			Properties p = new Properties();
			try (InputStream in = new FileInputStream(f)) {
				p.load(in);
			}
			LABELS.put(lang, p);
		}
	}

	private static LivenessEvent event(LivenessState s, Workflow w, ChallengeType c, Integer secs, QualityHint h) {
		return new LivenessEvent(s, w, 2, 3, c, secs, h);
	}

	private static String text(String lang, String key, Object... args) {
		String raw = LABELS.get(lang).getProperty(key);
		assertNotNull(lang + " is missing " + key, raw);
		return args.length == 0 ? raw : MessageFormat.format(raw, args);
	}

	// ---- the table ------------------------------------------------------------------------------------------

	@Test
	public void allSixteenStatesHaveAViewWithMessagesInEveryLanguage() {
		assertEquals(16, LivenessState.values().length);
		for (LivenessState s : LivenessState.values()) {
			View v = DesktopLivenessUiModel.of(event(s, Workflow.RESIDENT, ChallengeType.BLINK, 5, QualityHint.FAR),
					1);
			assertNotNull(s.name(), v.headlineKey);
			assertNotNull(s.name(), v.ring);
			assertNotNull(s.name(), v.actions);
			for (String lang : LANGS) {
				assertFalse(s + "/" + lang, text(lang, v.headlineKey, v.headlineArgs).trim().isEmpty());
				if (v.sublineKey != null) {
					assertFalse(s + "/" + lang, text(lang, v.sublineKey).trim().isEmpty());
				}
				assertFalse(text(lang, v.retryKey).trim().isEmpty());
			}
		}
	}

	@Test
	public void ringColourClassPerState() {
		assertEquals(Ring.POSITIONING, DesktopLivenessUiModel.ringFor(LivenessState.IDLE));
		assertEquals(Ring.POSITIONING, DesktopLivenessUiModel.ringFor(LivenessState.POSITIONING));
		assertEquals(Ring.POSITIONING, DesktopLivenessUiModel.ringFor(LivenessState.NO_FACE));
		assertEquals(Ring.POSITIONING, DesktopLivenessUiModel.ringFor(LivenessState.MULTIPLE_FACES));
		assertEquals(Ring.POSITIONING, DesktopLivenessUiModel.ringFor(LivenessState.POOR_QUALITY));
		assertEquals(Ring.CHECKING, DesktopLivenessUiModel.ringFor(LivenessState.PASSIVE_CHECK));
		assertEquals(Ring.CHALLENGE, DesktopLivenessUiModel.ringFor(LivenessState.CHALLENGE));
		assertEquals(Ring.SUCCESS, DesktopLivenessUiModel.ringFor(LivenessState.SUCCESS));
		for (LivenessState s : new LivenessState[] { LivenessState.LIVENESS_FAILED, LivenessState.PAD_FAILED,
				LivenessState.CHALLENGE_TIMEOUT, LivenessState.MAX_RETRIES, LivenessState.DEVICE_UNAVAILABLE,
				LivenessState.CAMERA_ERROR, LivenessState.INVALID_FRAMES, LivenessState.ENGINE_ERROR }) {
			assertEquals(s.name(), Ring.FAILURE, DesktopLivenessUiModel.ringFor(s));
		}
	}

	@Test
	public void buttonsPerState() {
		assertEquals(Actions.SUCCESS, DesktopLivenessUiModel.actionsFor(LivenessState.SUCCESS));
		assertEquals(Actions.MAX_RETRIES, DesktopLivenessUiModel.actionsFor(LivenessState.MAX_RETRIES));
		for (LivenessState s : new LivenessState[] { LivenessState.LIVENESS_FAILED, LivenessState.PAD_FAILED,
				LivenessState.CHALLENGE_TIMEOUT }) {
			assertEquals(s.name(), Actions.FAILURE, DesktopLivenessUiModel.actionsFor(s));
		}
		for (LivenessState s : new LivenessState[] { LivenessState.DEVICE_UNAVAILABLE, LivenessState.CAMERA_ERROR,
				LivenessState.INVALID_FRAMES, LivenessState.ENGINE_ERROR }) {
			assertEquals(s.name(), Actions.SYSTEM_ERROR, DesktopLivenessUiModel.actionsFor(s));
		}
		for (LivenessState s : new LivenessState[] { LivenessState.IDLE, LivenessState.POSITIONING,
				LivenessState.PASSIVE_CHECK, LivenessState.CHALLENGE, LivenessState.NO_FACE,
				LivenessState.MULTIPLE_FACES, LivenessState.POOR_QUALITY }) {
			assertEquals(s.name(), Actions.RUNNING, DesktopLivenessUiModel.actionsFor(s));
		}
	}

	@Test
	public void aMissingDeviceOffersRetryConnectionOtherErrorsTryAgain() {
		View missing = DesktopLivenessUiModel
				.of(event(LivenessState.DEVICE_UNAVAILABLE, Workflow.RESIDENT, null, null, null), 1);
		assertEquals("liveness_retry_connection", missing.retryKey);
		View camera = DesktopLivenessUiModel
				.of(event(LivenessState.CAMERA_ERROR, Workflow.RESIDENT, null, null, null), 1);
		assertEquals("liveness_try_again", camera.retryKey);
	}

	@Test
	public void challengeViewCarriesIconCountdownAndProgress() {
		View v = DesktopLivenessUiModel
				.of(event(LivenessState.CHALLENGE, Workflow.RESIDENT, ChallengeType.TURN_LEFT, 8, null), 1);
		assertEquals("liveness_challenge_turn_left", v.headlineKey);
		assertEquals(Icon.TURN_LEFT, v.icon);
		assertEquals(Integer.valueOf(8), v.secondsRemaining);
		assertEquals("liveness_challenge_number", v.progressKey);
		assertEquals("Challenge 1", text("en", v.progressKey, v.progressArgs));

		View second = DesktopLivenessUiModel
				.of(event(LivenessState.CHALLENGE, Workflow.RESIDENT, ChallengeType.BLINK, 8, null), 2);
		assertEquals("liveness_challenge_next", second.progressKey);
		assertEquals("Action detected. Challenge 2", text("en", second.progressKey, second.progressArgs));
	}

	@Test
	public void challengeCounterCountsPerAttempt() {
		ChallengeCounter c = new ChallengeCounter();
		assertEquals(1, c.update(new LivenessEvent(LivenessState.POSITIONING, Workflow.RESIDENT, 1, 3, null, null,
				null)));
		assertEquals(1, c.update(new LivenessEvent(LivenessState.CHALLENGE, Workflow.RESIDENT, 1, 3,
				ChallengeType.BLINK, 8, null)));
		assertEquals(1, c.update(new LivenessEvent(LivenessState.CHALLENGE, Workflow.RESIDENT, 1, 3,
				ChallengeType.BLINK, 7, null)));
		assertEquals(2, c.update(new LivenessEvent(LivenessState.CHALLENGE, Workflow.RESIDENT, 1, 3,
				ChallengeType.SMILE, 8, null)));
		// A new attempt starts again at 1.
		assertEquals(1, c.update(new LivenessEvent(LivenessState.CHALLENGE, Workflow.RESIDENT, 2, 3,
				ChallengeType.SMILE, 8, null)));
	}

	@Test
	public void successWordingDiffersForResidentAndStaff() {
		View resident = DesktopLivenessUiModel
				.of(event(LivenessState.SUCCESS, Workflow.RESIDENT, null, null, null), 1);
		assertEquals("liveness_state_success", resident.headlineKey);
		assertNull(resident.sublineKey);
		View staff = DesktopLivenessUiModel
				.of(event(LivenessState.SUCCESS, Workflow.OPERATOR, null, null, null), 1);
		assertEquals("liveness_state_success_auth", staff.headlineKey);
		assertEquals("liveness_state_success_auth_hint", staff.sublineKey);
		assertTrue(staff.success);
	}

	@Test
	public void poorQualityHintsAreGeneric() {
		assertEquals("liveness_hint_lighting", DesktopLivenessUiModel
				.of(event(LivenessState.POOR_QUALITY, Workflow.RESIDENT, null, null, QualityHint.LIGHTING), 1).headlineKey);
		assertEquals("liveness_hint_far", DesktopLivenessUiModel
				.of(event(LivenessState.POOR_QUALITY, Workflow.RESIDENT, null, null, QualityHint.FAR), 1).headlineKey);
		assertEquals("liveness_hint_blurry", DesktopLivenessUiModel
				.of(event(LivenessState.POOR_QUALITY, Workflow.RESIDENT, null, null, null), 1).headlineKey);
	}

	@Test
	public void maxRetriesMessageNamesTheAttemptCount() {
		View v = DesktopLivenessUiModel.of(event(LivenessState.MAX_RETRIES, Workflow.RESIDENT, null, null, null), 1);
		assertTrue(text("en", v.headlineKey, v.headlineArgs), text("en", v.headlineKey, v.headlineArgs).contains("3"));
	}

	@Test
	public void introHasAStartButtonAndWorkflowTitles() {
		View intro = DesktopLivenessUiModel.intro(3);
		assertEquals(Actions.INTRO, intro.actions);
		assertEquals("Before we start", text("en", intro.headlineKey));
		assertEquals("Resident registration", text("en", DesktopLivenessUiModel.workflowKey(Workflow.RESIDENT)));
		assertEquals("Operator login", text("en", DesktopLivenessUiModel.workflowKey(Workflow.OPERATOR)));
		assertEquals("Supervisor approval", text("en", DesktopLivenessUiModel.workflowKey(Workflow.SUPERVISOR)));
	}

	// ---- strings ---------------------------------------------------------------------------------------------

	@Test
	public void everyEnglishLivenessStringExistsInFrenchAndArabicWithTheSamePlaceholders() {
		Pattern placeholder = Pattern.compile("\\{(\\d+)\\}");
		int checked = 0;
		for (String key : LABELS.get("en").stringPropertyNames()) {
			if (!key.startsWith("liveness_")) {
				continue;
			}
			checked++;
			String en = LABELS.get("en").getProperty(key);
			for (String lang : new String[] { "fr", "ar" }) {
				String other = LABELS.get(lang).getProperty(key);
				assertNotNull(lang + " is missing " + key, other);
				assertFalse(lang + " empty " + key, other.trim().isEmpty());
				assertEquals(lang + " placeholders of " + key, placeholders(placeholder, en),
						placeholders(placeholder, other));
			}
		}
		assertTrue("liveness strings: " + checked, checked >= 50);
	}

	private static String placeholders(Pattern p, String s) {
		StringBuilder sb = new StringBuilder();
		Matcher m = p.matcher(s);
		java.util.TreeSet<String> set = new java.util.TreeSet<>();
		while (m.find()) {
			set.add(m.group(1));
		}
		for (String x : set) {
			sb.append(x).append(',');
		}
		return sb.toString();
	}

	@Test
	public void noStringRevealsScoresThresholdsModelsOrAttackTypes() {
		// Words that must never appear on screen (ISO/IEC 30107 privacy rule). Checked in English; numbers can
		// only come from the attempt / countdown arguments, never from a score.
		String[] forbidden = { "score", "percent", "threshold", "confidence", "probability", "model", "attack",
				"spoof", "presentation", "photo", "print", "replay", "mask", "deepfake", "video", "screen replay",
				"minifasnet", "blazeface", "mediapipe", "onnx", "pad " };
		for (String key : LABELS.get("en").stringPropertyNames()) {
			if (!key.startsWith("liveness_")) {
				continue;
			}
			String lower = LABELS.get("en").getProperty(key).toLowerCase();
			assertFalse(key + ": " + lower, lower.contains("%"));
			for (String bad : forbidden) {
				assertFalse(key + " mentions '" + bad + "': " + lower, lower.contains(bad));
			}
		}
	}

	@Test
	public void aFailedAttemptNeverExplainsWhyItFailed() {
		// A spoofed attempt and a plain liveness failure read almost the same: nothing says "fake" or "photo".
		View pad = DesktopLivenessUiModel.of(event(LivenessState.PAD_FAILED, Workflow.RESIDENT, null, null, null), 1);
		View fail = DesktopLivenessUiModel
				.of(event(LivenessState.LIVENESS_FAILED, Workflow.RESIDENT, null, null, null), 1);
		String a = text("en", pad.headlineKey).toLowerCase();
		String b = text("en", fail.headlineKey).toLowerCase();
		assertTrue(a, a.contains("could not"));
		assertTrue(b, b.contains("could not"));
		assertEquals(pad.actions, fail.actions);
	}
}
