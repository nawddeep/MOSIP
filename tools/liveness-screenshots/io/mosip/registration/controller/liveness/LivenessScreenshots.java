package io.mosip.registration.controller.liveness;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;

import io.mosip.registration.liveness.DesktopLivenessService;
import io.mosip.registration.liveness.DesktopLivenessUiModel;
import io.mosip.registration.liveness.DesktopLivenessUiModel.View;
import io.mosip.registration.livenessmanager.model.ChallengeType;
import io.mosip.registration.livenessmanager.model.LivenessEvent;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.QualityHint;
import io.mosip.registration.livenessmanager.model.Workflow;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Renders the real Desktop liveness screen (FXML, CSS, label bundles and controller) for every state and saves
 * PNGs. No SBI device, engine or MOSIP environment is involved: the service is a stub and the preview is a
 * synthetic silhouette, never a real person's image.
 *
 * <pre>
 *   LivenessScreenshots OUT_DIR [en|fr|ar]
 * </pre>
 *
 * JavaFX 11.0.2 ships macOS natives for Intel only, so on Apple silicon run it with an x64 JDK 11 under Rosetta.
 * See tools/liveness-screenshots/README.md.
 */
public final class LivenessScreenshots {

	private LivenessScreenshots() {
	}

	public static void main(String[] args) throws Exception {
		File out = new File(args.length > 0 ? args[0] : "liveness-screenshots");
		String lang = args.length > 1 ? args[1] : "en";
		if (!out.isDirectory() && !out.mkdirs()) {
			throw new IllegalStateException("cannot create " + out);
		}
		CountDownLatch done = new CountDownLatch(1);
		Platform.startup(() -> {
			try {
				run(out, lang, done);
			} catch (Exception e) {
				e.printStackTrace();
				done.countDown();
			}
		});
		done.await();
		Platform.exit();
	}

	private static final class Scenario {
		final String name;
		final View view;
		final Runnable after;

		Scenario(String name, View view, Runnable after) {
			this.name = name;
			this.view = view;
			this.after = after;
		}
	}

	private static LivenessEvent e(LivenessState s, Workflow w, ChallengeType c, Integer secs, QualityHint h) {
		return new LivenessEvent(s, w, 2, 3, c, secs, h);
	}

	private static void run(File out, String lang, CountDownLatch done) throws Exception {
		ResourceBundle bundle = ResourceBundle.getBundle("labels", new Locale(lang));
		DesktopLivenessService stub = new DesktopLivenessService(null) {
			@Override
			public int getMaxAttempts(Workflow workflow) {
				return 3;
			}

			@Override
			public boolean isEnabled(Workflow workflow) {
				return true;
			}
		};
		final FaceLivenessController[] holder = new FaceLivenessController[1];
		FaceLivenessController c = new FaceLivenessController() {
			@Override
			Parent loadRoot() throws java.io.IOException {
				FXMLLoader loader = new FXMLLoader(FaceLivenessController.class.getResource("/fxml/FaceLiveness.fxml"),
						bundle);
				loader.setControllerFactory(type -> holder[0]);
				return loader.load();
			}
		};
		holder[0] = c;
		Field f = FaceLivenessController.class.getDeclaredField("service");
		f.setAccessible(true);
		f.set(c, stub);

		// The scene is rendered off-screen: JavaFX 11.0.2 crashes in its macOS window code on current macOS
		// versions, and Scene.snapshot does not need a visible window.
		Stage stage = c.prepare(Workflow.RESIDENT);
		c.ovalView().getPreview().setImage(silhouette());
		// JavaFX 11.0.2 cannot map the macOS system UI font on current macOS and prints garbage. For these
		// screenshots only, name an installed font (-Dliveness.font=Arial); the product stylesheet is unchanged.
		String font = System.getProperty("liveness.font");
		if (font != null) {
			stage.getScene().getRoot().setStyle("-fx-font-family: '" + font + "';");
		}

		Workflow r = Workflow.RESIDENT;
		List<Scenario> list = new ArrayList<>();
		list.add(new Scenario("01-intro", DesktopLivenessUiModel.intro(3), null));
		list.add(new Scenario("02-positioning", DesktopLivenessUiModel.of(e(LivenessState.POSITIONING, r, null, null, null), 1), null));
		list.add(new Scenario("03-no-face", DesktopLivenessUiModel.of(e(LivenessState.NO_FACE, r, null, null, null), 1), null));
		list.add(new Scenario("04-multiple-faces", DesktopLivenessUiModel.of(e(LivenessState.MULTIPLE_FACES, r, null, null, null), 1), null));
		list.add(new Scenario("05-poor-quality-lighting", DesktopLivenessUiModel.of(e(LivenessState.POOR_QUALITY, r, null, null, QualityHint.LIGHTING), 1), null));
		list.add(new Scenario("06-checking", DesktopLivenessUiModel.of(e(LivenessState.PASSIVE_CHECK, r, null, null, null), 1), null));
		list.add(new Scenario("07-challenge-blink", DesktopLivenessUiModel.of(e(LivenessState.CHALLENGE, r, ChallengeType.BLINK, 6, null), 1),
				() -> {
					c.updateTimer(e(LivenessState.CHALLENGE, r, ChallengeType.BLINK, 6, null));
					c.ovalView().setTimerFraction(0.75);
				}));
		list.add(new Scenario("08-challenge-smile-2", DesktopLivenessUiModel.of(e(LivenessState.CHALLENGE, r, ChallengeType.SMILE, 4, null), 2),
				() -> {
					c.updateTimer(e(LivenessState.CHALLENGE, r, ChallengeType.SMILE, 4, null));
					c.ovalView().setTimerFraction(0.5);
				}));
		list.add(new Scenario("09-challenge-turn-left", DesktopLivenessUiModel.of(e(LivenessState.CHALLENGE, r, ChallengeType.TURN_LEFT, 8, null), 1),
				() -> {
					c.updateTimer(e(LivenessState.CHALLENGE, r, ChallengeType.TURN_LEFT, 8, null));
					c.ovalView().setTimerFraction(1.0);
				}));
		list.add(new Scenario("10-challenge-turn-right", DesktopLivenessUiModel.of(e(LivenessState.CHALLENGE, r, ChallengeType.TURN_RIGHT, 2, null), 1),
				() -> {
					c.updateTimer(e(LivenessState.CHALLENGE, r, ChallengeType.TURN_RIGHT, 2, null));
					c.ovalView().setTimerFraction(0.25);
				}));
		list.add(new Scenario("11-challenge-timeout", DesktopLivenessUiModel.of(e(LivenessState.CHALLENGE_TIMEOUT, r, null, null, null), 1), null));
		list.add(new Scenario("12-liveness-failed", DesktopLivenessUiModel.of(e(LivenessState.LIVENESS_FAILED, r, null, null, null), 1), null));
		list.add(new Scenario("13-pad-failed", DesktopLivenessUiModel.of(e(LivenessState.PAD_FAILED, r, null, null, null), 1), null));
		list.add(new Scenario("14-max-retries", DesktopLivenessUiModel.of(new LivenessEvent(LivenessState.MAX_RETRIES, r, 3, 3, null, null, null), 1), null));
		list.add(new Scenario("15-recovery", DesktopLivenessUiModel.of(new LivenessEvent(LivenessState.MAX_RETRIES, r, 3, 3, null, null, null), 1),
				() -> invoke(c, "onRecovery")));
		list.add(new Scenario("16-device-unavailable", DesktopLivenessUiModel.of(e(LivenessState.DEVICE_UNAVAILABLE, r, null, null, null), 1), null));
		list.add(new Scenario("17-camera-error", DesktopLivenessUiModel.of(e(LivenessState.CAMERA_ERROR, r, null, null, null), 1), null));
		list.add(new Scenario("18-invalid-frames", DesktopLivenessUiModel.of(e(LivenessState.INVALID_FRAMES, r, null, null, null), 1), null));
		list.add(new Scenario("19-engine-error", DesktopLivenessUiModel.of(e(LivenessState.ENGINE_ERROR, r, null, null, null), 1), null));
		list.add(new Scenario("20-success-resident", DesktopLivenessUiModel.of(e(LivenessState.SUCCESS, r, null, null, null), 1), null));
		list.add(new Scenario("21-success-operator", DesktopLivenessUiModel.of(e(LivenessState.SUCCESS, Workflow.OPERATOR, null, null, null), 1), null));

		step(list, 0, c, stage, out, lang, done);
	}

	private static void step(List<Scenario> list, int i, FaceLivenessController c, Stage stage, File out,
			String lang, CountDownLatch done) {
		if (i >= list.size()) {
			done.countDown();
			return;
		}
		Scenario s = list.get(i);
		invokeQuiet(c, "onRecoveryStay");
		c.render(s.view);
		if (s.after != null) {
			s.after.run();
		}
		PauseTransition wait = new PauseTransition(Duration.millis(1100));
		wait.setOnFinished(ev -> {
			try {
				WritableImage img = stage.getScene().snapshot(null);
				File file = new File(out, "liveness-" + lang + "-" + s.name + ".png");
				ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", file);
				System.out.println("wrote " + file);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			step(list, i + 1, c, stage, out, lang, done);
		});
		wait.play();
	}

	private static void invoke(Object target, String method) {
		try {
			Method m = FaceLivenessController.class.getDeclaredMethod(method);
			m.setAccessible(true);
			m.invoke(target);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void invokeQuiet(Object target, String method) {
		try {
			invoke(target, method);
		} catch (RuntimeException ignored) {
			// not available before the first render
		}
	}

	/** A neutral head-and-shoulders silhouette: stands in for the camera image, never a real person. */
	private static Image silhouette() {
		int w = 640;
		int h = 480;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, new Color(58, 66, 78), 0, h, new Color(26, 30, 36)));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(112, 120, 132));
		g.fillOval(w / 2 - 70, h / 2 - 120, 140, 180);
		g.setColor(new Color(92, 100, 112));
		g.fillOval(w / 2 - 170, h / 2 + 50, 340, 300);
		g.dispose();
		return SwingFXUtils.toFXImage(img, null);
	}
}
