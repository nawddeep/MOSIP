import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

/// Shared Desktop-matched building blocks. Spec: docs/ui/design-system.md §5–6.
/// Presentation only: callers pass their existing (localized) strings.

typedef _T = RegistrationTheme;

/// Tracked section label (Desktop mono label). The text is not upper-cased:
/// ui-test finds elements by @text, so existing strings must stay as-is.
class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key, this.trailing});
  final String text;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final label = Semantics(
        header: true, child: Text(text, style: _T.label));
    if (trailing == null) return label;
    return Row(children: [Expanded(child: label), trailing!]);
  }
}

/// White surface with a 1px hairline and 8px radius. Flat.
class Panel extends StatelessWidget {
  const Panel(
      {super.key,
      required this.child,
      this.padding = const EdgeInsets.all(_T.s4),
      this.onTap,
      this.color = _T.card,
      this.borderColor = _T.line});
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final Color color;
  final Color borderColor;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color,
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(_T.rLg),
          side: BorderSide(color: borderColor)),
      clipBehavior: Clip.antiAlias,
      child: onTap == null
          ? Padding(padding: padding, child: child)
          : InkWell(
              onTap: onTap, child: Padding(padding: padding, child: child)),
    );
  }
}

enum StatusTone { ok, warn, err, info, neutral }

class _ToneColors {
  const _ToneColors(this.fg, this.bg, this.border, this.icon);
  final Color fg;
  final Color bg;
  final Color border;
  final IconData icon;
}

_ToneColors _tone(StatusTone t) {
  switch (t) {
    case StatusTone.ok:
      return const _ToneColors(
          _T.ok, _T.okTint, _T.okLine, Icons.check_circle_outline);
    case StatusTone.warn:
      return const _ToneColors(
          _T.warn, _T.warnTint, _T.warnLine, Icons.schedule_outlined);
    case StatusTone.err:
      return const _ToneColors(
          _T.err, _T.errTint, _T.errLine, Icons.error_outline);
    case StatusTone.info:
      return const _ToneColors(
          _T.accentPress, _T.accentTint, _T.accent, Icons.info_outline);
    case StatusTone.neutral:
      return const _ToneColors(
          _T.muted, _T.sunken, _T.lineStrong, Icons.radio_button_unchecked);
  }
}

/// Small status tag: icon + word, never colour alone.
class StatusBadge extends StatelessWidget {
  const StatusBadge(this.text, {super.key, required this.tone, this.icon});
  final String text;
  final StatusTone tone;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final c = _tone(tone);
    return Container(
      constraints: const BoxConstraints(minHeight: 22),
      padding: const EdgeInsets.symmetric(horizontal: _T.s2, vertical: 2),
      decoration: BoxDecoration(
          color: c.bg,
          borderRadius: BorderRadius.circular(_T.rBadge),
          border: Border.all(color: c.border)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon ?? c.icon, size: 14, color: c.fg),
        const SizedBox(width: _T.s1),
        Flexible(
            child: Text(text,
                style:
                    _T.xs.copyWith(color: c.fg, fontWeight: FontWeight.w600))),
      ]),
    );
  }
}

/// Tinted inline message with a leading status icon and optional action.
class InlineAlert extends StatelessWidget {
  const InlineAlert(
      {super.key,
      required this.message,
      this.tone = StatusTone.info,
      this.action});
  final String message;
  final StatusTone tone;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final c = _tone(tone);
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.all(_T.s3),
        decoration: BoxDecoration(
            color: c.bg,
            borderRadius: BorderRadius.circular(_T.r),
            border: Border.all(color: c.border)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(c.icon, size: _T.iconInline, color: c.fg),
          const SizedBox(width: _T.s2),
          Expanded(
              child: Text(message, style: _T.small.copyWith(color: _T.ink))),
          if (action != null) ...[const SizedBox(width: _T.s2), action!],
        ]),
      ),
    );
  }
}

/// Segmented step bar: done = ink, current = accent, todo = sunken.
/// [semanticsLabel] should be the caller's existing step title.
class StepProgress extends StatelessWidget {
  const StepProgress(
      {super.key,
      required this.total,
      required this.current,
      this.semanticsLabel});
  final int total;
  final int current;
  final String? semanticsLabel;

  @override
  Widget build(BuildContext context) {
    if (total <= 1) return const SizedBox.shrink();
    return Semantics(
      label: semanticsLabel,
      value: '${current + 1}/$total',
      child: ExcludeSemantics(
        child: Row(
          children: List<Widget>.generate(total, (i) {
            final color = i < current
                ? _T.ink
                : i == current
                    ? _T.accent
                    : _T.sunken;
            return Expanded(
              child: Container(
                height: 4,
                margin: EdgeInsetsDirectional.only(
                    end: i == total - 1 ? 0 : _T.s1),
                decoration: BoxDecoration(
                    color: color, borderRadius: BorderRadius.circular(2)),
              ),
            );
          }),
        ),
      ),
    );
  }
}

/// Explains what is missing and what to do; at most one action.
class EmptyState extends StatelessWidget {
  const EmptyState(
      {super.key,
      required this.icon,
      required this.title,
      this.message,
      this.action});
  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: _T.s8, horizontal: _T.s4),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 32, color: _T.muted),
        const SizedBox(height: _T.s3),
        Text(title, style: _T.heading, textAlign: TextAlign.center),
        if (message != null) ...[
          const SizedBox(height: _T.s1),
          Text(message!, style: _T.small, textAlign: TextAlign.center),
        ],
        if (action != null) ...[const SizedBox(height: _T.s4), action!],
      ]),
    );
  }
}

/// Leading icon tile used in task cards and list rows.
class IconTile extends StatelessWidget {
  const IconTile({super.key, required this.child, this.size = _T.iconTile});
  final Widget child;
  final double size;

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
            color: _T.sunken, borderRadius: BorderRadius.circular(_T.r)),
        child: IconTheme.merge(
            data: const IconThemeData(color: _T.ink, size: _T.iconInline),
            child: child),
      );
}

// ---- Motion (the six allowed moments; all honour reduced motion) ----------

/// One-shot fade + 8px rise on first build. [delay] staggers lists.
class FadeRise extends StatefulWidget {
  const FadeRise(
      {super.key,
      required this.child,
      this.delay = Duration.zero,
      this.duration = _T.base});
  final Widget child;
  final Duration delay;
  final Duration duration;

  /// Delay for item [index] in a staggered list, capped so the whole
  /// sequence finishes under 600ms.
  static Duration staggerFor(int index) => _T.stagger * math.min(index, 7);

  @override
  State<FadeRise> createState() => _FadeRiseState();
}

class _FadeRiseState extends State<FadeRise>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c =
      AnimationController(vsync: this, duration: widget.duration);
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (_T.reduceMotion(context)) {
      _c.value = 1;
    } else if (widget.delay == Duration.zero) {
      _c.forward();
    } else {
      Future<void>.delayed(widget.delay, () {
        if (mounted) _c.forward();
      });
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      child: widget.child,
      builder: (context, child) {
        final t = _T.ease.transform(_c.value);
        return Opacity(
          opacity: t,
          child:
              Transform.translate(offset: Offset(0, 8 * (1 - t)), child: child),
        );
      },
    );
  }
}

/// Sync icon that rotates while [active]; static under reduced motion.
class SyncSpinner extends StatefulWidget {
  const SyncSpinner(
      {super.key, required this.active, this.size = _T.iconInline, this.color});
  final bool active;
  final double size;
  final Color? color;

  @override
  State<SyncSpinner> createState() => _SyncSpinnerState();
}

class _SyncSpinnerState extends State<SyncSpinner>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 1200));

  void _sync() {
    final run = widget.active && !_T.reduceMotion(context);
    if (run && !_c.isAnimating) {
      _c.repeat();
    } else if (!run && _c.isAnimating) {
      _c.stop();
      _c.value = 0;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(SyncSpinner oldWidget) {
    super.didUpdateWidget(oldWidget);
    _sync();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => RotationTransition(
        // Material's sync arrows point counter-clockwise.
        turns: Tween<double>(begin: 0, end: -1).animate(_c),
        child: Icon(Icons.sync, size: widget.size, color: widget.color),
      );
}

/// Self-drawing check in a tinted circle (capture success, registration done).
class SuccessCheck extends StatefulWidget {
  const SuccessCheck({super.key, this.size = 56, this.semanticsLabel});
  final double size;
  final String? semanticsLabel;

  @override
  State<SuccessCheck> createState() => _SuccessCheckState();
}

class _SuccessCheckState extends State<SuccessCheck>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c =
      AnimationController(vsync: this, duration: _T.slow);
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    _T.reduceMotion(context) ? _c.value = 1 : _c.forward();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Semantics(
        label: widget.semanticsLabel,
        image: widget.semanticsLabel != null,
        child: AnimatedBuilder(
          animation: _c,
          builder: (context, _) => CustomPaint(
            size: Size.square(widget.size),
            painter: _CheckPainter(_c.value),
          ),
        ),
      );
}

class _CheckPainter extends CustomPainter {
  _CheckPainter(this.t);
  final double t;

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final e = _T.ease.transform(t);
    final radius = size.width / 2 * (0.85 + 0.15 * e);
    canvas.drawCircle(c, radius, Paint()..color = _T.okTint);
    canvas.drawCircle(
        c,
        radius - 0.5,
        Paint()
          ..style = PaintingStyle.stroke
          ..color = _T.okLine);
    if (t <= 0) return;
    final s = size.width * 0.22;
    final path = Path()
      ..moveTo(c.dx - s, c.dy + s * 0.05)
      ..lineTo(c.dx - s * 0.25, c.dy + s * 0.75)
      ..lineTo(c.dx + s, c.dy - s * 0.6);
    final m = path.computeMetrics().first;
    canvas.drawPath(
        m.extractPath(0, m.length * e),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = size.width * 0.07
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round
          ..color = _T.ok);
  }

  @override
  bool shouldRepaint(_CheckPainter old) => old.t != t;
}
