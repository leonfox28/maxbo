import 'package:flutter/material.dart';

class MePage extends StatelessWidget {
  const MePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('我')),
      body: const Center(child: Text('登录与个人信息将在 M1 实现')),
    );
  }
}
