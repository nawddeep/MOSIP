import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/sync_provider.dart';
import 'package:registration_client/ui/theme/components.dart';
import 'package:registration_client/ui/theme/registration_theme.dart';

class HomePageCard extends StatelessWidget {
  const HomePageCard(
      {super.key,
      required this.icon,
      required this.title,
      required this.index,
      required this.ontap,
      required this.subtitle});
  final Widget icon;
  final String title;
  final int index;
  final String? subtitle;
  final VoidCallback ontap;

  @override
  Widget build(BuildContext context) {
    String? detail = subtitle;
    if (detail == null && index == 0) {
      final raw = context.watch<SyncProvider>().lastSuccessfulSyncTime;
      final time = DateTime.tryParse(raw);
      if (time != null)
        detail = DateFormat('d MMM, h:mm a').format(time.toLocal());
    }
    // Desktop task card: hairline panel, icon tile, title, arrow. Cards rise
    // in with a short stagger on first build (motion moment 2).
    return FadeRise(
        delay: FadeRise.staggerFor(index),
        child: Padding(
            padding: const EdgeInsets.only(bottom: RegistrationTheme.s3),
            child: Panel(
              onTap: ontap,
              padding: const EdgeInsets.symmetric(
                  horizontal: RegistrationTheme.s4,
                  vertical: RegistrationTheme.s4),
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                    minHeight: RegistrationTheme.iconTile),
                child: Row(children: [
                  IconTile(
                      child: Padding(
                          padding: const EdgeInsets.all(RegistrationTheme.s2),
                          child: ExcludeSemantics(child: icon))),
                  const SizedBox(width: RegistrationTheme.s4),
                  Expanded(
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                        Text(title, style: RegistrationTheme.heading),
                        if (detail != null && detail.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(detail, style: RegistrationTheme.xs),
                        ],
                      ])),
                  const SizedBox(width: RegistrationTheme.s3),
                  const Icon(Icons.arrow_forward_rounded,
                      size: RegistrationTheme.iconInline,
                      color: RegistrationTheme.muted),
                ]),
              ),
            )));
  }
}
