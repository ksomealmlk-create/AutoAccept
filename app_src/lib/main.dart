import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const App());

class App extends StatelessWidget {
  const App({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData.dark(useMaterial3: true),
      builder: (context, child) =>
          Directionality(textDirection: TextDirection.rtl, child: child!),
      home: const Home(),
    );
  }
}

class Home extends StatefulWidget {
  const Home({super.key});

  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> with WidgetsBindingObserver {
  static const _ch = MethodChannel('autoaccept/native');
  final _words = TextEditingController();
  bool _enabled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _words.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _load() async {
    final w = await _ch.invokeMethod<String>('getWords');
    _words.text = w ?? 'اقبل,تطابق';
    await _refresh();
  }

  Future<void> _refresh() async {
    final e = await _ch.invokeMethod<bool>('isEnabled');
    if (mounted) setState(() => _enabled = e ?? false);
  }

  Future<void> _save() async {
    await _ch.invokeMethod('setWords', _words.text);
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('تم الحفظ')));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('القبول التلقائي')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: ListView(
          children: [
            Card(
              child: ListTile(
                leading: Icon(
                  _enabled ? Icons.check_circle : Icons.cancel,
                  color: _enabled ? Colors.green : Colors.red,
                ),
                title: Text(_enabled ? 'الخدمة شغالة' : 'الخدمة مو مفعّلة'),
                subtitle: const Text('فعّلها من إعدادات سهولة الوصول'),
              ),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () => _ch.invokeMethod('openSettings'),
              icon: const Icon(Icons.settings_accessibility),
              label: const Text('فتح إعدادات سهولة الوصول'),
            ),
            const SizedBox(height: 28),
            const Text('الكلمات اللي يضغط عليها (افصل بينها بفاصلة)'),
            const SizedBox(height: 8),
            TextField(
              controller: _words,
              decoration: const InputDecoration(border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            OutlinedButton(onPressed: _save, child: const Text('حفظ')),
          ],
        ),
      ),
    );
  }
}
