package io.mosip.registration.controller.liveness;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.controller.device.Streamer;
import io.mosip.registration.liveness.DesktopLivenessService;
import io.mosip.registration.liveness.DesktopLivenessUiModel;
import io.mosip.registration.liveness.DesktopLivenessUiModel.Actions;
import io.mosip.registration.liveness.DesktopLivenessUiModel.ChallengeCounter;
import io.mosip.registration.liveness.DesktopLivenessUiModel.Icon;
import io.mosip.registration.liveness.DesktopLivenessUiModel.View;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;
import io.mosip.registration.service.bio.BioService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * The face liveness screen for the Desktop client: a dark popup with the live SBI preview in an oval, one
 * instruction at a time, and the same buttons as the Android screen. It only <em>renders</em>
 * {@link DesktopLivenessUiModel}; the decisions are made by the liveness engine behind
 * {@link DesktopLivenessService}.
 *
 * <p>
 * Callers use {@link #ensurePass(Workflow)} to run the check and {@link #consumePass(Workflow, String)} to take
 * the single-use pass at the moment the protected action starts. The gate decides: {@code consumePass} is
 * {@code true} only when liveness is disabled for the workflow or a fresh pass really exists, and it audits
 * REG-LIV-006 otherwise. So the rule is enforced in Java, not by hiding UI.
 *
 * <p>
 * Nothing on screen carries a score, percentage, threshold, model name or attack type.
 */
@Controller
public class FaceLivenessController {

	private static final Logger LOGGER = AppConfig.getLogger(FaceLivenessController.class);
	private static final String FXML = "/fxml/FaceLiveness.fxml";
	private static final String CSS = "/liveness.css";

	@Autowired
	private DesktopLivenessService service;
	@Autowired
	private Streamer streamer;
	@Autowired
	private BioService bioService;

	@FXML
	private ResourceBundle resources;
	@FXML
	private StackPane root;
	@FXML
	private BorderPane content;
	@FXML
	private Button backButton;
	@FXML
	private Label captionLabel;
	@FXML
	private Label workflowLabel;
	@FXML
	private Label attemptLabel;
	@FXML
	private StackPane ovalHolder;
	@FXML
	private StackPane iconHolder;
	@FXML
	private Label progressLabel;
	@FXML
	private Label headlineLabel;
	@FXML
	private Label countdownLabel;
	@FXML
	private Label sublineLabel;
	@FXML
	private HBox buttonBar;
	@FXML
	private Button startButton;
	@FXML
	private Button tryAgainButton;
	@FXML
	private Button continueButton;
	@FXML
	private Button recoveryButton;
	@FXML
	private Button cancelButton;
	@FXML
	private VBox recoveryPanel;
	@FXML
	private Label recoveryTitle;
	@FXML
	private Label recoveryBody;

	private Stage stage;
	private LivenessOvalView oval;
	private Workflow workflow;
	private boolean passed;
	private ChallengeCounter counter;
	private int challengeTotal;
	private Integer lastSeconds;
	private Icon shownIcon;

	// ---- entry points used by the gates ---------------------------------------------------------------------

	/**
	 * Runs the liveness check in a popup and waits for it. {@code true} when liveness is disabled for the
	 * workflow (nothing is shown) or the person passed and pressed Continue (a fresh pass was issued).
	 */
	public boolean ensurePass(Workflow workflow) {
		if (!service.isEnabled(workflow)) {
			return true;
		}
		if (Platform.isFxApplicationThread()) {
			return show(workflow);
		}
		FutureTask<Boolean> task = new FutureTask<>(() -> show(workflow));
		Platform.runLater(task);
		try {
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (ExecutionException e) {
			LOGGER.error("Liveness screen failed", e);
			return false;
		}
	}

	/**
	 * Takes the single-use pass. {@code true} when liveness is disabled for the workflow or a fresh pass existed;
	 * otherwise audits REG-LIV-006 and returns {@code false}.
	 */
	public boolean consumePass(Workflow workflow, String fieldId) {
		return service.checkAndConsumePass(workflow, fieldId);
	}

	/**
	 * Check and consume in one step, for flows that proceed straight away (operator login, supervisor approval).
	 * The gate has the last word: whatever the screen reported, only a fresh pass lets the caller continue.
	 */
	public boolean requirePass(Workflow workflow, String fieldId) {
		ensurePass(workflow);
		return consumePass(workflow, fieldId);
	}

	public boolean isEnabled(Workflow workflow) {
		return service.isEnabled(workflow);
	}

	/** Alert text for "face liveness is required to continue" (messages_*.properties, LIVENESS_REQUIRED). */
	public String requiredMessage() {
		return RegistrationUIConstants.getMessageLanguageSpecific("LIVENESS_REQUIRED");
	}

	// ---- screen ---------------------------------------------------------------------------------------------

	private boolean show(Workflow workflow) {
		try {
			prepare(workflow);
			stage.showAndWait();
			return passed;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Face liveness screen could not be shown", e);
			service.cancel();
			return false;
		}
	}

	/** Loads the FXML. A seam for the screenshot tool, which runs without the Spring context. */
	Parent loadRoot() throws IOException {
		return BaseController.load(getClass().getResource(FXML));
	}

	/** Builds the window without showing it (also used by the screenshot tool). */
	Stage prepare(Workflow workflow) throws IOException {
		this.workflow = workflow;
		this.passed = false;
		this.counter = new ChallengeCounter();
		this.challengeTotal = 0;
		this.lastSeconds = null;
		this.shownIcon = null;
		Parent rootNode = loadRoot();
		Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
		double height = Math.min(760, bounds.getHeight() - 60);
		double width = Math.min(880, bounds.getWidth() - 60);
		double ovalWidth = Math.max(180, Math.min(300, (height - 340) / 1.3 - 24));

		oval = new LivenessOvalView(ovalWidth);
		ovalHolder.getChildren().setAll(oval);
		if (resources != null && "ar".equals(resources.getLocale().getLanguage())) {
			content.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
			backButton.setText("\u2192"); // "back" points right when the layout is mirrored
		}
		recoveryPanel.setVisible(false);

		Scene scene = new Scene(rootNode, width, height, Color.web("#0B0B0C"));
		scene.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
		stage = new Stage(StageStyle.UNDECORATED);
		stage.initModality(Modality.APPLICATION_MODAL);
		Window owner = focusedWindow();
		if (owner != null) {
			stage.initOwner(owner);
		}
		stage.setResizable(false);
		stage.setScene(scene);
		stage.setOnHidden(e -> {
			// Closing without Continue withdraws any pass and releases the SBI stream.
			if (!passed) {
				service.cancel();
			}
			oval.stop();
		});

		captionLabel.setText(t("liveness_caption"));
		workflowLabel.setText(t(DesktopLivenessUiModel.workflowKey(workflow)));
		render(DesktopLivenessUiModel.intro(service.getMaxAttempts(workflow)));
		return stage;
	}

	/** The oval, for the screenshot tool. */
	LivenessOvalView ovalView() {
		return oval;
	}

	private static Window focusedWindow() {
		for (Window w : Window.getWindows()) {
			if (w.isShowing() && w.isFocused()) {
				return w;
			}
		}
		return ClientApplication.getPrimaryStage();
	}

	// ---- actions --------------------------------------------------------------------------------------------

	@FXML
	private void onStart() {
		showWaiting();
		service.start(workflow, new StreamerSbiTap(streamer, bioService, oval.getPreview()),
				e -> Platform.runLater(() -> onEvent(e)));
	}

	@FXML
	private void onRetry() {
		showWaiting();
		service.retry();
	}

	@FXML
	private void onContinue() {
		passed = true;
		stage.close();
	}

	@FXML
	private void onCancel() {
		passed = false;
		service.cancel();
		stage.close();
	}

	@FXML
	private void onRecovery() {
		recoveryTitle.setText(t("liveness_recovery_title"));
		recoveryBody.setText(t(workflow == Workflow.RESIDENT ? "liveness_recovery_body_resident"
				: "liveness_recovery_body_staff"));
		recoveryPanel.setVisible(true);
		recoveryPanel.toFront();
	}

	@FXML
	private void onRecoveryStay() {
		recoveryPanel.setVisible(false);
	}

	@FXML
	private void onRecoveryReturn() {
		onCancel();
	}

	// ---- rendering ------------------------------------------------------------------------------------------

	private void onEvent(LivenessEvent event) {
		if (stage == null || !stage.isShowing()) {
			return;
		}
		try {
			render(DesktopLivenessUiModel.of(event, counter.update(event)));
			if (event.getState() == LivenessState.CHALLENGE) {
				updateTimer(event);
			}
		} catch (RuntimeException e) {
			LOGGER.error("Face liveness screen update failed", e);
		}
	}

	private void showWaiting() {
		startButton.setDisable(true);
		tryAgainButton.setDisable(true);
		headlineLabel.setText(t("liveness_starting"));
		sublineLabel.setText("");
	}

	void render(View v) {
		headlineLabel.setText(t(v.headlineKey, v.headlineArgs));
		boolean hasSub = v.sublineKey != null;
		sublineLabel.setText(hasSub ? t(v.sublineKey) : "");
		setShown(sublineLabel, hasSub);

		boolean challenge = v.progressKey != null;
		progressLabel.setText(challenge ? t(v.progressKey, v.progressArgs) : "");
		setShown(progressLabel, challenge);
		if (v.secondsRemaining == null) {
			countdownLabel.setText("");
			lastSeconds = null;
		}
		setShown(countdownLabel, v.secondsRemaining != null);

		boolean started = v.actions != Actions.INTRO;
		attemptLabel.setText(started ? t("liveness_attempt", v.attempt, v.maxAttempts) : "");
		setShown(attemptLabel, started);

		oval.setRing(v.ring);
		showIcon(v.icon);

		tryAgainButton.setText(t(v.retryKey));
		startButton.setDisable(false);
		tryAgainButton.setDisable(false);
		setShown(startButton, v.actions == Actions.INTRO);
		setShown(tryAgainButton, v.actions == Actions.FAILURE || v.actions == Actions.SYSTEM_ERROR);
		setShown(continueButton, v.actions == Actions.SUCCESS);
		setShown(recoveryButton, v.actions == Actions.MAX_RETRIES);
		setShown(cancelButton, v.actions != Actions.SUCCESS && v.actions != Actions.MAX_RETRIES);

		Button primary = v.actions == Actions.INTRO ? startButton
				: v.actions == Actions.SUCCESS ? continueButton
						: v.actions == Actions.MAX_RETRIES ? recoveryButton
								: v.actions == Actions.RUNNING ? cancelButton : tryAgainButton;
		primary.requestFocus();
		headlineLabel.setAccessibleText(headlineLabel.getText());
	}

	/** Drains the ring between the once-per-second updates of the engine. */
	void updateTimer(LivenessEvent e) {
		Integer secs = e.getSecondsRemaining();
		if (secs == null) {
			return;
		}
		challengeTotal = Math.max(challengeTotal, secs);
		if (secs.equals(lastSeconds)) {
			return;
		}
		lastSeconds = secs;
		double total = Math.max(1, challengeTotal);
		oval.drainTo(secs / total, Math.max(0, secs - 1) / total);
		countdownLabel.setText(t("liveness_semantics_countdown", secs));
		countdownLabel.setAccessibleText(countdownLabel.getText());
	}

	private void showIcon(Icon icon) {
		if (icon == shownIcon) {
			return;
		}
		shownIcon = icon;
		iconHolder.getChildren().clear();
		if (icon == Icon.NONE) {
			return;
		}
		SVGPath path = new SVGPath();
		path.setContent(iconPath(icon));
		path.setFill(Color.TRANSPARENT);
		path.setStroke(LivenessOvalView.CHALLENGE);
		path.setStrokeWidth(2.2);
		path.setScaleX(1.9);
		path.setScaleY(1.9);
		iconHolder.getChildren().add(path);
	}

	/** 24x24 line icons: eye, smiling face, arrows. */
	static String iconPath(Icon icon) {
		switch (icon) {
		case BLINK:
			return "M2 12 C5 6 9 4 12 4 C15 4 19 6 22 12 C19 18 15 20 12 20 C9 20 5 18 2 12 Z M15 12 A3 3 0 1 1 9 12 A3 3 0 1 1 15 12";
		case SMILE:
			return "M22 12 A10 10 0 1 1 2 12 A10 10 0 1 1 22 12 M7.5 14 C9 17.5 15 17.5 16.5 14 M9 9.5 L9 9.6 M15 9.5 L15 9.6";
		case TURN_LEFT:
			return "M21 12 L5 12 M11 5 L4 12 L11 19";
		case TURN_RIGHT:
			return "M3 12 L19 12 M13 5 L20 12 L13 19";
		default:
			return "";
		}
	}

	private static void setShown(Node n, boolean shown) {
		n.setVisible(shown);
		n.setManaged(shown);
	}

	private String t(String key, Object... args) {
		String raw = resources != null && resources.containsKey(key) ? resources.getString(key) : key;
		return args.length == 0 ? raw : MessageFormat.format(raw, args);
	}
}
