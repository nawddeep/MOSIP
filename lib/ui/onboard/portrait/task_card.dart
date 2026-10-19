import 'package:flutter/material.dart';
import 'package:registration_client/ui/onboard/widgets/home_page_card.dart';

class TaskCard extends StatelessWidget {
  const TaskCard(
      {super.key,
      required this.icon,
      required this.title,
      required this.index,
      required this.ontap,
      required this.subtitle});
  final Widget icon;
  final String title;
  final int index;
  final Function ontap;
  final String? subtitle;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: HomePageCard(
            icon: icon,
            title: title,
            index: index,
            ontap: () => ontap(),
            subtitle: subtitle),
      );
}
