package io.mosip.registration.controller.liveness;

import io.mosip.registration.liveness.DesktopLivenessUiModel.Ring;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

/**
 * The oval guide: the live SBI preview clipped to an oval with a state ring around it.
 *
 * <ul>
 * <li>positioning: ring breathes</li>
 * <li>checking: a short arc orbits the oval</li>
 * <li>challenge: the ring drains with the time left</li>
 * <li>success: the ring fills and a check mark draws itself</li>
 * <li>failure: solid ring and one short shake</li>
 * </ul>
 *
 * With {@code -Dliveness.reduceMotion=true} nothing loops and changes are instant. The preview is mirrored so
 * the person sees themselves as in a mirror ("turn left" means their own left).
 */
final class LivenessOvalView extends StackPane {

	static final Color TRACK = Color.web("#8E8E93", 0.18);
	static final Color POSITIONING = Color.web("#86A6F5", 0.8);
	static final Color CHECKING = Color.web("#86A6F5");
	static final Color CHALLENGE = Color.web("#CDA866");
	static final Color SUCCESS = Color.web("#86AE90");
	static final Color FAILURE = Color.web("#D48A82");

	private static final double STROKE = 5;
	private static final double MARGIN = 12;

	private final boolean reduceMotion = Boolean.getBoolean("liveness.reduceMotion");
	private final double perimeter;
	private final ImageView preview = new ImageView();
	private final Path ring = new Path();
	private final Path check = new Path();
	private Timeline loop;
	private Timeline fill;
	private Timeline drain;
	private Ring current;

	LivenessOvalView(double width) {
		double height = width * 1.3;
		double rx = width / 2;
		double ry = height / 2;
		perimeter = Math.PI * (3 * (rx + ry) - Math.sqrt((3 * rx + ry) * (rx + 3 * ry)));
		double outerW = width + 2 * MARGIN;
		double outerH = height + 2 * MARGIN;
		setPrefSize(outerW, outerH);
		setMinSize(outerW, outerH);
		setMaxSize(outerW, outerH);
		setAlignment(Pos.CENTER);

		Ellipse backdrop = new Ellipse(rx, ry, rx, ry);
		backdrop.setFill(Color.web("#161618"));

		preview.setFitHeight(height);
		preview.setPreserveRatio(true);
		preview.setScaleX(-1);
		preview.setSmooth(true);
		StackPane previewBox = new StackPane(preview);
		previewBox.setPrefSize(width, height);
		previewBox.setMinSize(width, height);
		previewBox.setMaxSize(width, height);
		previewBox.setClip(new Ellipse(rx, ry, rx, ry));

		Ellipse track = new Ellipse(rx, ry, rx, ry);
		track.setFill(Color.TRANSPARENT);
		track.setStroke(TRACK);
		track.setStrokeWidth(STROKE);

		// Starts at 12 o'clock and runs clockwise, so dashes drain and fill from the top.
		ring.getElements().addAll(new MoveTo(rx, 0), new ArcTo(rx, ry, 0, rx, 2 * ry, false, true),
				new ArcTo(rx, ry, 0, rx, 0, false, true));
		ring.setFill(Color.TRANSPARENT);
		ring.setStrokeWidth(STROKE);
		ring.setStrokeLineCap(StrokeLineCap.ROUND);

		double s = width * 0.16;
		check.getElements().addAll(new MoveTo(rx - s * 0.55, ry + s * 0.02), new LineTo(rx - s * 0.12, ry + s * 0.45),
				new LineTo(rx + s * 0.62, ry - s * 0.42));
		check.setStroke(SUCCESS);
		check.setStrokeWidth(6);
		check.setStrokeLineCap(StrokeLineCap.ROUND);
		check.setStrokeLineJoin(StrokeLineJoin.ROUND);
		check.setFill(Color.TRANSPARENT);
		check.setVisible(false);

		Pane canvas = new Pane(backdrop, previewBox, track, ring, check);
		canvas.setPrefSize(width, height);
		canvas.setMinSize(width, height);
		canvas.setMaxSize(width, height);
		getChildren().add(canvas);
		setRing(Ring.POSITIONING);
	}

	/** The ImageView the SBI {@code Streamer} draws the live preview into. */
	ImageView getPreview() {
		return preview;
	}

	/** Shows the ring for a state; asking again for the same ring keeps what is running. */
	void setRing(Ring next) {
		if (next == current) {
			return;
		}
		Ring previous = current;
		current = next;
		stopAnimations();
		check.setVisible(false);
		ring.setOpacity(1);
		ring.setStrokeWidth(STROKE);
		solid();
		switch (next) {
		case POSITIONING:
			ring.setStroke(POSITIONING);
			if (!reduceMotion) {
				ring.setOpacity(0.55);
				loop = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(ring.opacityProperty(), 0.55)),
						new KeyFrame(Duration.millis(1200),
								new KeyValue(ring.opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
				loop.setAutoReverse(true);
				loop.setCycleCount(Animation.INDEFINITE);
				loop.play();
			}
			break;
		case CHECKING:
			ring.setStroke(CHECKING);
			ring.setStrokeWidth(STROKE + 1);
			ring.getStrokeDashArray().setAll(perimeter * 0.12, perimeter * 0.88);
			if (!reduceMotion) {
				loop = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(ring.strokeDashOffsetProperty(), 0)),
						new KeyFrame(Duration.millis(2400),
								new KeyValue(ring.strokeDashOffsetProperty(), -perimeter, Interpolator.LINEAR)));
				loop.setCycleCount(Animation.INDEFINITE);
				loop.play();
			}
			break;
		case CHALLENGE:
			ring.setStroke(CHALLENGE);
			break;
		case SUCCESS:
			ring.setStroke(SUCCESS);
			check.setVisible(true);
			if (reduceMotion) {
				check.getStrokeDashArray().clear();
			} else {
				animateSuccess();
			}
			break;
		case FAILURE:
		default:
			ring.setStroke(FAILURE);
			if (!reduceMotion && previous != null) {
				shake();
			}
			break;
		}
	}

	/** Draws {@code fraction} (1 = full) of the ring; used for the challenge time left. */
	void setTimerFraction(double fraction) {
		if (current == Ring.CHALLENGE) {
			setArc(Math.max(0, Math.min(1, fraction)));
		}
	}

	/** Drains the ring smoothly from {@code from} to {@code to} over one second. */
	void drainTo(double from, double to) {
		if (current != Ring.CHALLENGE) {
			return;
		}
		if (drain != null) {
			drain.stop();
		}
		setArc(from);
		if (reduceMotion) {
			return;
		}
		SimpleDoubleProperty p = new SimpleDoubleProperty(from);
		p.addListener((o, a, b) -> setArc(b.doubleValue()));
		drain = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(p, from)),
				new KeyFrame(Duration.millis(1000), new KeyValue(p, to, Interpolator.LINEAR)));
		drain.play();
	}

	void stop() {
		stopAnimations();
	}

	private void solid() {
		ring.getStrokeDashArray().clear();
		ring.setStrokeDashOffset(0);
	}

	private void setArc(double fraction) {
		if (fraction >= 0.999) {
			solid();
		} else {
			ring.getStrokeDashArray().setAll(Math.max(0.001, perimeter * fraction), perimeter);
			ring.setStrokeDashOffset(0);
		}
	}

	private void animateSuccess() {
		SimpleDoubleProperty p = new SimpleDoubleProperty(0);
		p.addListener((o, a, b) -> setArc(b.doubleValue()));
		setArc(0);
		fill = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(p, 0)),
				new KeyFrame(Duration.millis(750), new KeyValue(p, 1, Interpolator.EASE_OUT)));
		fill.play();

		double len = 80;
		check.getStrokeDashArray().setAll(len, len);
		SimpleDoubleProperty offset = new SimpleDoubleProperty(len);
		offset.addListener((o, a, b) -> check.setStrokeDashOffset(b.doubleValue()));
		check.setStrokeDashOffset(len);
		Timeline draw = new Timeline(new KeyFrame(Duration.millis(250), new KeyValue(offset, len)),
				new KeyFrame(Duration.millis(750), new KeyValue(offset, 0, Interpolator.EASE_OUT)));
		draw.setOnFinished(e -> check.getStrokeDashArray().clear());
		draw.play();
	}

	private void shake() {
		TranslateTransition t = new TranslateTransition(Duration.millis(90), this);
		t.setFromX(0);
		t.setByX(10);
		t.setCycleCount(4);
		t.setAutoReverse(true);
		t.setOnFinished(e -> setTranslateX(0));
		t.play();
	}

	private void stopAnimations() {
		for (Timeline t : new Timeline[] { loop, fill, drain }) {
			if (t != null) {
				t.stop();
			}
		}
		loop = null;
		fill = null;
		drain = null;
		setTranslateX(0);
	}
}
