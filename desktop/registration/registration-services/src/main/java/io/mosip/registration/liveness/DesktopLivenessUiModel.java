package io.mosip.registration.liveness;

import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * What the Desktop liveness screen shows for a given liveness state: which message, ring colour, icon and
 * buttons. The same table as the Android screen ({@code liveness_copy.dart}), without any JavaFX, so it is
 * unit-tested and the screen only renders it.
 *
 * <p>
 * Privacy: nothing here can carry a score, percentage, threshold, model name or attack type; a view only
 * holds message keys, a state class and the attempt / countdown numbers the person is meant to see.
 * Message keys are the {@code liveness_*} entries of {@code labels_*.properties}.
 */
public final class DesktopLivenessUiModel {

	/** Colour / animation of the oval guide. */
	public enum Ring {
		POSITIONING, CHECKING, CHALLENGE, SUCCESS, FAILURE
	}

	/** Which buttons are offered. */
	public enum Actions {
		/** Start. */
		INTRO,
		/** Cancel. */
		RUNNING,
		/** Continue. */
		SUCCESS,
		/** Recovery options. */
		MAX_RETRIES,
		/** Try again, Cancel. */
		FAILURE,
		/** Retry connection (device missing) or Try again, Cancel. */
		SYSTEM_ERROR
	}

	/** Icon above the instruction while a challenge runs. */
	public enum Icon {
		NONE, BLINK, SMILE, TURN_LEFT, TURN_RIGHT
	}

	/** One rendering of the screen. */
	public static final class View {
		public final String headlineKey;
		public final Object[] headlineArgs;
		/** {@code null} when there is no second line. */
		public final String sublineKey;
		public final Ring ring;
		public final Actions actions;
		public final Icon icon;
		/** Seconds left in the current challenge, or {@code null}. */
		public final Integer secondsRemaining;
		/** "Challenge n" caption key, or {@code null} outside a challenge. */
		public final String progressKey;
		public final Object[] progressArgs;
		/** Key of the primary retry button for {@link Actions#SYSTEM_ERROR} / {@link Actions#FAILURE}. */
		public final String retryKey;
		public final int attempt;
		public final int maxAttempts;
		public final boolean success;

		View(String headlineKey, Object[] headlineArgs, String sublineKey, Ring ring, Actions actions, Icon icon,
				Integer secondsRemaining, String progressKey, Object[] progressArgs, String retryKey, int attempt,
				int maxAttempts, boolean success) {
			this.headlineKey = headlineKey;
			this.headlineArgs = headlineArgs;
			this.sublineKey = sublineKey;
			this.ring = ring;
			this.actions = actions;
			this.icon = icon;
			this.secondsRemaining = secondsRemaining;
			this.progressKey = progressKey;
			this.progressArgs = progressArgs;
			this.retryKey = retryKey;
			this.attempt = attempt;
			this.maxAttempts = maxAttempts;
			this.success = success;
		}
	}

	private static final Object[] NONE = new Object[0];

	private DesktopLivenessUiModel() {
	}

	/** Title of the screen for a workflow. */
	public static String workflowKey(Workflow workflow) {
		switch (workflow) {
		case OPERATOR:
			return "liveness_workflow_operator";
		case SUPERVISOR:
			return "liveness_workflow_supervisor";
		case RESIDENT:
		default:
			return "liveness_workflow_resident";
		}
	}

	/** Before anything has started: "Before we start" with a Start button. */
	public static View intro(int maxAttempts) {
		return new View("liveness_intro_title", NONE, "liveness_intro_body", Ring.POSITIONING, Actions.INTRO,
				Icon.NONE, null, null, NONE, "liveness_try_again", 1, maxAttempts, false);
	}

	/**
	 * @param challengeNumber
	 *            1 for the first challenge of the attempt, then 2, ... (see {@link ChallengeCounter})
	 */
	public static View of(LivenessEvent e, int challengeNumber) {
		LivenessState s = e.getState();
		int attempt = e.getAttempt();
		int max = e.getMaxAttempts();
		String head;
		Object[] args = NONE;
		String sub = null;
		Icon icon = Icon.NONE;
		Integer seconds = null;
		String progressKey = null;
		Object[] progressArgs = NONE;

		switch (s) {
		case IDLE:
		case POSITIONING:
			head = "liveness_state_position_face";
			sub = "liveness_state_look_camera";
			break;
		case PASSIVE_CHECK:
			head = "liveness_state_checking";
			sub = "liveness_state_hold_still";
			break;
		case CHALLENGE:
			head = challengeKey(e.getChallengeType());
			icon = iconFor(e.getChallengeType());
			seconds = e.getSecondsRemaining();
			progressKey = challengeNumber > 1 ? "liveness_challenge_next" : "liveness_challenge_number";
			progressArgs = new Object[] { Math.max(1, challengeNumber) };
			break;
		case CHALLENGE_TIMEOUT:
			head = "liveness_state_timeout";
			break;
		case NO_FACE:
			head = "liveness_state_no_face";
			break;
		case MULTIPLE_FACES:
			head = "liveness_state_multiple_faces";
			break;
		case POOR_QUALITY:
			head = hintKey(e.getHint());
			break;
		case LIVENESS_FAILED:
			head = "liveness_state_failed";
			break;
		case PAD_FAILED:
			head = "liveness_state_pad_failed";
			break;
		case MAX_RETRIES:
			head = "liveness_state_max_retries";
			args = new Object[] { max };
			break;
		case DEVICE_UNAVAILABLE:
			head = "liveness_state_camera_unavailable";
			break;
		case CAMERA_ERROR:
			head = "liveness_state_camera_error";
			sub = "liveness_state_camera_error_hint";
			break;
		case INVALID_FRAMES:
			head = "liveness_state_invalid_frames";
			sub = "liveness_state_invalid_frames_hint";
			break;
		case ENGINE_ERROR:
			head = "liveness_state_engine_error";
			break;
		case SUCCESS:
		default:
			if (e.getWorkflow() != Workflow.RESIDENT) {
				head = "liveness_state_success_auth";
				sub = "liveness_state_success_auth_hint";
			} else {
				head = "liveness_state_success";
			}
			break;
		}
		String retryKey = s == LivenessState.DEVICE_UNAVAILABLE ? "liveness_retry_connection" : "liveness_try_again";
		return new View(head, args, sub, ringFor(s), actionsFor(s), icon, seconds, progressKey, progressArgs,
				retryKey, attempt, max, s == LivenessState.SUCCESS);
	}

	public static Ring ringFor(LivenessState s) {
		switch (s) {
		case PASSIVE_CHECK:
			return Ring.CHECKING;
		case CHALLENGE:
			return Ring.CHALLENGE;
		case SUCCESS:
			return Ring.SUCCESS;
		case LIVENESS_FAILED:
		case PAD_FAILED:
		case CHALLENGE_TIMEOUT:
		case MAX_RETRIES:
		case DEVICE_UNAVAILABLE:
		case CAMERA_ERROR:
		case INVALID_FRAMES:
		case ENGINE_ERROR:
			return Ring.FAILURE;
		case IDLE:
		case POSITIONING:
		case NO_FACE:
		case MULTIPLE_FACES:
		case POOR_QUALITY:
		default:
			return Ring.POSITIONING;
		}
	}

	public static Actions actionsFor(LivenessState s) {
		if (s == LivenessState.SUCCESS) {
			return Actions.SUCCESS;
		}
		if (s == LivenessState.MAX_RETRIES) {
			return Actions.MAX_RETRIES;
		}
		switch (s.getKind()) {
		case ATTEMPT_FAILURE:
			return Actions.FAILURE;
		case SYSTEM_ERROR:
			return Actions.SYSTEM_ERROR;
		default:
			return Actions.RUNNING;
		}
	}

	static String challengeKey(ChallengeType t) {
		if (t == null) {
			return "liveness_challenge_generic";
		}
		switch (t) {
		case BLINK:
			return "liveness_challenge_blink";
		case SMILE:
			return "liveness_challenge_smile";
		case TURN_LEFT:
			return "liveness_challenge_turn_left";
		case TURN_RIGHT:
			return "liveness_challenge_turn_right";
		default:
			return "liveness_challenge_generic";
		}
	}

	static Icon iconFor(ChallengeType t) {
		if (t == null) {
			return Icon.NONE;
		}
		switch (t) {
		case BLINK:
			return Icon.BLINK;
		case SMILE:
			return Icon.SMILE;
		case TURN_LEFT:
			return Icon.TURN_LEFT;
		case TURN_RIGHT:
			return Icon.TURN_RIGHT;
		default:
			return Icon.NONE;
		}
	}

	static String hintKey(QualityHint h) {
		if (h == QualityHint.LIGHTING) {
			return "liveness_hint_lighting";
		}
		if (h == QualityHint.FAR) {
			return "liveness_hint_far";
		}
		return "liveness_hint_blurry";
	}

	/**
	 * Counts the challenges of an attempt for the "Challenge n" caption: a new challenge starts when the type
	 * changes, and the count restarts with each attempt.
	 */
	public static final class ChallengeCounter {
		private int attempt = -1;
		private ChallengeType last;
		private int number;

		public int update(LivenessEvent e) {
			if (e.getAttempt() != attempt) {
				attempt = e.getAttempt();
				last = null;
				number = 0;
			}
			if (e.getState() == LivenessState.CHALLENGE && e.getChallengeType() != null
					&& e.getChallengeType() != last) {
				last = e.getChallengeType();
				number++;
			}
			return Math.max(1, number);
		}
	}
}
