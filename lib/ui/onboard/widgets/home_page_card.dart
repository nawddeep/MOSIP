import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:registration_client/provider/sync_provider.dart';
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
    return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Material(
          color: Colors.white,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
              side: const BorderSide(color: RegistrationTheme.line)),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: ontap,
            child: Padding(
                padding: const EdgeInsets.all(18),
                child: Row(children: [
                  Container(
                      width: 48,
                      height: 48,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                          color: RegistrationTheme.softBlue,
                          borderRadius: BorderRadius.circular(14)),
                      child: ExcludeSemantics(child: icon)),
                  const SizedBox(width: 16),
                  Expanded(
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                        Text(title,
                            style: const TextStyle(
                                fontFamily: 'IBMPlexSans',
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                                color: RegistrationTheme.ink)),
                        if (detail != null && detail.isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text(detail,
                              style: const TextStyle(
                                  fontSize: 13,
                                  color: RegistrationTheme.muted)),
                        ],
                      ])),
                  const SizedBox(width: 12),
                  const Icon(Icons.arrow_forward_rounded,
                      size: 18, color: RegistrationTheme.muted),
                ])),
          ),
        ));
  }
}
