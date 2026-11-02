import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const _ch = MethodChannel('autoaccept/native');

const kNeighborhoods = [
  'حي النصر', 'حي المهندسين', 'حي أبو طالب', 'حي الغزي', 'حي الجمعية',
  'حي السلام', 'حي الجامعة', 'حي الوفاء', 'حي الفرات', 'حي الغدير',
  'حي العدالة', 'حي الأمير', 'حي الاشتراكي', 'حي السواق', 'حي الحوراء',
  'حي الزهراء', 'حي القادسية', 'عماد سكر', 'حي الأنصار', 'حي القدس',
  'مجمع بوابة النجف', 'مجمع طيبة', 'حي النداء', 'حي الجديدة', 'حي العسكري',
  'حي المكرمة', 'حي الميلاد', 'حي الهندية', 'حي الجزيرة', 'حي السلام الجديد',
  'حي المعارض', 'حي النفط', 'حي العلماء', 'حي الحسين', 'حي الحنانة',
  'حي الصحة', 'حي الإسكان', 'حي السعد', 'حي عدن', 'حي المعلمين',
  'حي الإمام المهدي', 'حي الحرفيين', 'حي الشرطة', 'حي العمارات السكنية',
  'الحي الصناعي',
];

/// كل الإعدادات تنحفظ فورًا (نصوص) ويقراها الـ Service من نفس المكان.
class Store extends ChangeNotifier {
  Map<String, String> m = {};
  bool serviceOn = false;

  Future<void> load() async {
    final r = await _ch.invokeMapMethod<String, String>('getAll');
    m = r ?? {};
    if (!m.containsKey('nbAll')) await set('nbAll', kNeighborhoods.join('|'));
    serviceOn = await _ch.invokeMethod<bool>('isEnabled') ?? false;
    notifyListeners();
  }

  bool b(String k, bool d) => (m[k] ?? (d ? '1' : '0')) == '1';
  String s(String k, String d) => m[k] ?? d;
  int i(String k) => int.tryParse(m[k] ?? '0') ?? 0;

  Future<void> set(String k, String v) async {
    m[k] = v;
    notifyListeners();
    await _ch.invokeMethod('set', {'key': k, 'value': v});
  }

  Future<void> setB(String k, bool v) => set(k, v ? '1' : '0');

  Set<String> get selectedNb {
    final v = m['nbSelected'];
    if (v == null) return kNeighborhoods.toSet();
    return v.isEmpty ? <String>{} : v.split('|').toSet();
  }

  Future<void> setNb(Set<String> s) => set('nbSelected', s.join('|'));
}

final store = Store();

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  store.load();
  runApp(const App());
}

// ---------------- الثيم: أسود/كريمي + ذهبي ----------------
bool _dark(BuildContext c) => Theme.of(c).brightness == Brightness.dark;
Color _gold(BuildContext c) =>
    _dark(c) ? const Color(0xFFD4AF37) : const Color(0xFFB8901F);
Color _card(BuildContext c) =>
    _dark(c) ? const Color(0xFF16161A) : Colors.white;
Color _border(BuildContext c) =>
    _dark(c) ? const Color(0xFF34301F) : const Color(0xFFE3D9BC);
Color _sub(BuildContext c) =>
    _dark(c) ? const Color(0xFFC2BBA8) : const Color(0xFF5A5340);
Color _text(BuildContext c) =>
    _dark(c) ? const Color(0xFFF6F2E7) : const Color(0xFF1B1708);

ThemeData _theme(Brightness br) {
  final d = br == Brightness.dark;
  final bg = d ? const Color(0xFF0D0D0F) : const Color(0xFFF7F3E8);
  final gold = d ? const Color(0xFFD4AF37) : const Color(0xFFB8901F);
  return ThemeData(
    brightness: br,
    useMaterial3: true,
    scaffoldBackgroundColor: bg,
    colorScheme: ColorScheme.fromSeed(seedColor: gold, brightness: br),
    appBarTheme: AppBarTheme(
      backgroundColor: bg,
      elevation: 0,
      centerTitle: true,
      foregroundColor: d ? const Color(0xFFF6F2E7) : const Color(0xFF1B1708),
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? gold : null),
      trackColor: WidgetStateProperty.resolveWith((s) =>
          s.contains(WidgetState.selected) ? gold.withOpacity(.35) : null),
    ),
  );
}

class App extends StatelessWidget {
  const App({super.key});
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (_, __) {
        final t = store.s('theme', 'system');
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          title: 'صگار كريم',
          theme: _theme(Brightness.light),
          darkTheme: _theme(Brightness.dark),
          themeMode: t == 'light'
              ? ThemeMode.light
              : t == 'dark'
                  ? ThemeMode.dark
                  : ThemeMode.system,
          builder: (c, child) =>
              Directionality(textDirection: TextDirection.rtl, child: child!),
          home: const Home(),
        );
      },
    );
  }
}

// ---------------- عناصر مشتركة ----------------
class Box extends StatelessWidget {
  final Widget child;
  final EdgeInsets pad;
  const Box({super.key, required this.child, this.pad = const EdgeInsets.all(14)});
  @override
  Widget build(BuildContext c) => Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: pad,
        decoration: BoxDecoration(
          color: _card(c),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: _border(c)),
        ),
        child: child,
      );
}

class IconDot extends StatelessWidget {
  final String icon;
  const IconDot(this.icon, {super.key});
  @override
  Widget build(BuildContext c) => Container(
        width: 40,
        height: 40,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: _gold(c).withOpacity(.15),
          shape: BoxShape.circle,
        ),
        child: Text(icon, style: TextStyle(fontSize: 18, color: _gold(c))),
      );
}

class Section extends StatelessWidget {
  final String t;
  const Section(this.t, {super.key});
  @override
  Widget build(BuildContext c) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 14, 4, 8),
        child: Text(t,
            style: TextStyle(
                color: _gold(c), fontSize: 14, fontWeight: FontWeight.bold)),
      );
}

class SwitchRow extends StatelessWidget {
  final String icon, title, sub, k;
  final bool def;
  const SwitchRow(this.icon, this.title, this.sub, this.k,
      {super.key, this.def = true});
  @override
  Widget build(BuildContext c) {
    final on = store.b(k, def);
    return Box(
      child: Row(children: [
        IconDot(icon),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title,
                style: TextStyle(
                    color: _text(c), fontWeight: FontWeight.bold, fontSize: 14)),
            const SizedBox(height: 2),
            Text(sub, style: TextStyle(color: _sub(c), fontSize: 12)),
          ]),
        ),
        Switch(value: on, onChanged: (v) => store.setB(k, v)),
      ]),
    );
  }
}

class Expand extends StatefulWidget {
  final String icon, title, summary;
  final Widget body;
  const Expand(this.icon, this.title, this.summary, this.body, {super.key});
  @override
  State<Expand> createState() => _ExpandState();
}

class _ExpandState extends State<Expand> {
  bool open = false;
  @override
  Widget build(BuildContext c) => Box(
        pad: EdgeInsets.zero,
        child: Column(children: [
          InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () => setState(() => open = !open),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(children: [
                IconDot(widget.icon),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(widget.title,
                            style: TextStyle(
                                color: _text(c),
                                fontWeight: FontWeight.bold,
                                fontSize: 14)),
                        Text(widget.summary,
                            style: TextStyle(color: _gold(c), fontSize: 12)),
                      ]),
                ),
                Text(open ? '▴' : '▾',
                    style: TextStyle(color: _sub(c), fontSize: 18)),
              ]),
            ),
          ),
          if (open)
            Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 12),
                child: widget.body),
        ]),
      );
}

// ---------------- الرئيسية ----------------
class Home extends StatefulWidget {
  const Home({super.key});
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) store.load();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (c, _) {
        final on = store.serviceOn;
        final master = store.b('enabled', true);
        return Scaffold(
          appBar: AppBar(title: const Text('صگار كريم')),
          body: ListView(padding: const EdgeInsets.all(16), children: [
            Box(
              pad: const EdgeInsets.all(20),
              child: Column(children: [
                Text('حالة النظام',
                    style: TextStyle(color: _sub(c), fontSize: 13)),
                const SizedBox(height: 6),
                Text(on && master ? 'شغّال' : 'مطفي',
                    style: TextStyle(
                        color: on && master
                            ? (_dark(c)
                                ? const Color(0xFF4ADE80)
                                : const Color(0xFF15803D))
                            : (_dark(c)
                                ? const Color(0xFFFB7185)
                                : const Color(0xFFDC2626)),
                        fontSize: 26,
                        fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                GestureDetector(
                  onTap: () => _ch.invokeMethod('openSettings'),
                  child: Container(
                    width: 84,
                    height: 84,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: _gold(c),
                      boxShadow: [
                        BoxShadow(
                            color: _gold(c).withOpacity(.4), blurRadius: 24)
                      ],
                    ),
                    child: Text(on ? '✓' : '▶',
                        style: const TextStyle(
                            fontSize: 34, color: Color(0xFF17130A))),
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                    on
                        ? 'الخدمة مفعّلة في سهولة الوصول'
                        : 'اضغط لتفعيل الخدمة من سهولة الوصول',
                    style: TextStyle(color: _sub(c), fontSize: 12)),
              ]),
            ),
            Row(children: [
              Expanded(child: _Stat('مقبول', store.i('stat_accept'), true)),
              const SizedBox(width: 10),
              Expanded(child: _Stat('متجاهل', store.i('stat_reject'), false)),
            ]),
            const SizedBox(height: 4),
            _Nav('⚙', 'الإعدادات', 'الأتمتة، المسافة، الأحياء، المظهر',
                () => Navigator.push(c,
                    MaterialPageRoute(builder: (_) => const SettingsPage()))),
            _Nav('▥', 'الإحصائيات', 'عدد المقبول والمتجاهل',
                () => Navigator.push(c,
                    MaterialPageRoute(builder: (_) => const StatsPage()))),
          ]),
        );
      },
    );
  }
}

class _Stat extends StatelessWidget {
  final String label;
  final int n;
  final bool good;
  const _Stat(this.label, this.n, this.good);
  @override
  Widget build(BuildContext c) {
    final col = good
        ? (_dark(c) ? const Color(0xFF4ADE80) : const Color(0xFF15803D))
        : (_dark(c) ? const Color(0xFFFB7185) : const Color(0xFFDC2626));
    return Box(
      child: Column(children: [
        Text('$n',
            style: TextStyle(
                color: col, fontSize: 28, fontWeight: FontWeight.bold)),
        Text(label, style: TextStyle(color: _sub(c), fontSize: 12)),
      ]),
    );
  }
}

class _Nav extends StatelessWidget {
  final String icon, title, sub;
  final VoidCallback onTap;
  const _Nav(this.icon, this.title, this.sub, this.onTap);
  @override
  Widget build(BuildContext c) => InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Box(
          child: Row(children: [
            IconDot(icon),
            const SizedBox(width: 12),
            Expanded(
              child:
                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title,
                    style: TextStyle(
                        color: _text(c),
                        fontWeight: FontWeight.bold,
                        fontSize: 15)),
                Text(sub, style: TextStyle(color: _sub(c), fontSize: 12)),
              ]),
            ),
            Text('‹', style: TextStyle(color: _sub(c), fontSize: 22)),
          ]),
        ),
      );
}

// ---------------- الإعدادات (Uber العراق) ----------------
class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  static final dists = <double, String>{
    1.0: 'حتى 1 كم',
    2.0: 'حتى 2 كم',
    2.5: 'حتى 2.5 كم',
    2.75: 'حتى 2.75 كم',
    3.0: 'حتى 3 كم',
    4.0: 'حتى 4 كم',
    5.0: 'حتى 5 كم',
    6.0: 'حتى 6 كم',
    7.0: 'حتى 7 كم',
    8.0: 'حتى 8 كم',
    9.0: 'حتى 9 كم',
    10.0: 'حتى 10 كم',
    -1.0: 'بدون حد للمسافة',
  };

  static final mins = <double, String>{
    1.0: 'حتى 1 دقيقة',
    2.0: 'حتى 2 دقيقة',
    3.0: 'حتى 3 دقائق',
    4.0: 'حتى 4 دقائق',
    5.0: 'حتى 5 دقائق',
    6.0: 'حتى 6 دقائق',
    7.0: 'حتى 7 دقائق',
    8.0: 'حتى 8 دقائق',
    9.0: 'حتى 9 دقائق',
    10.0: 'حتى 10 دقائق',
    -1.0: 'بدون حد للوقت',
  };

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (c, _) {
        final km = double.tryParse(store.s('maxKm', '3')) ?? 3.0;
        final mn = double.tryParse(store.s('maxMin', '10')) ?? 10.0;
        final sel = store.selectedNb;
        return Scaffold(
          appBar: AppBar(title: const Text('الإعدادات')),
          body: ListView(padding: const EdgeInsets.all(16), children: [
            const Section('الأتمتة'),
            const SwitchRow('⏻', 'تشغيل الأتمتة', 'المفتاح الرئيسي لكل الميزات',
                'enabled'),
            const SwitchRow('✓', 'قبول الرحلات ضمن المسافة',
                'يضغط قبول تلقائيًا للرحلة المناسبة', 'accept'),
            const SwitchRow('×', 'تجاهل الرحلات خارج المسافة',
                'يضغط «لا، شكرًا» تلقائيًا', 'reject',
                def: false),
            const SwitchRow('✓', 'إغلاق رسالة موافق',
                'يعالج رسالة أخذ الحجز من سائق آخر', 'closeOk'),
            const SwitchRow('▥', 'تسجيل الإحصائيات',
                'حفظ عمليات القبول والتجاهل', 'stats'),
            const SwitchRow('♪', 'تنبيه صوتي عند القبول',
                'ثلاث نغمات عند قبول الطلب', 'sound',
                def: false),
            const SwitchRow('◉', 'زر عائم',
                'زر يشغّل ويطفي الأتمتة فوق أي تطبيق (اسحبه لأي مكان)',
                'floating'),
            const SwitchRow('◎', 'ضغط بالإحداثيات إذا الزر مخفي',
                'يلمس مكان زر القبول المعتاد لما يظهر الطلب وما يلقى الزر',
                'tapFallback'),
            const SwitchRow('؟', 'قبول إذا ما انقرت المسافة',
                'إذا التطبيق ما قدر يقرا المسافة من الشاشة يقبل بدل ما يتجاهل',
                'unknownOk'),
            Box(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                        'ارتفاع نقطة الضغط: ${store.s('tapY', '87.3')}% من الشاشة',
                        style: TextStyle(color: _text(c), fontSize: 13)),
                    Slider(
                      min: 75,
                      max: 97,
                      divisions: 44,
                      value: (double.tryParse(store.s('tapY', '87.3')) ?? 87.3)
                          .clamp(75.0, 97.0)
                          .toDouble(),
                      onChanged: (v) => store.set('tapY', v.toStringAsFixed(1)),
                    ),
                  ]),
            ),
            const Section('المسافة'),
            Expand(
              '↔',
              'أقصى مسافة للطلب',
              dists[km] ?? 'حتى 3 كم',
              Column(children: [
                for (final e in dists.entries)
                  InkWell(
                    onTap: () => store.set('maxKm', e.key.toString()),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 11),
                      child: Row(children: [
                        Icon(
                            km == e.key
                                ? Icons.radio_button_checked
                                : Icons.radio_button_off,
                            color: km == e.key ? _gold(c) : _sub(c),
                            size: 20),
                        const SizedBox(width: 10),
                        Text(e.value,
                            style: TextStyle(
                                color: e.key < 0 ? _gold(c) : _text(c),
                                fontWeight: e.key < 0
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                fontSize: 13)),
                      ]),
                    ),
                  ),
              ]),
            ),
            const Section('وقت الوصول'),
            Expand(
              '⏱',
              'أقصى وقت للوصول للزبون',
              mins[mn] ?? 'حتى 10 دقائق',
              Column(children: [
                for (final e in mins.entries)
                  InkWell(
                    onTap: () => store.set('maxMin', e.key.toString()),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 11),
                      child: Row(children: [
                        Icon(
                            mn == e.key
                                ? Icons.radio_button_checked
                                : Icons.radio_button_off,
                            color: mn == e.key ? _gold(c) : _sub(c),
                            size: 20),
                        const SizedBox(width: 10),
                        Text(e.value,
                            style: TextStyle(
                                color: e.key < 0 ? _gold(c) : _text(c),
                                fontWeight: e.key < 0
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                fontSize: 13)),
                      ]),
                    ),
                  ),
              ]),
            ),
            const Section('الأحياء'),
            const SwitchRow('⌂', 'فلتر أحياء النجف',
                'حي مكان الزبون فقط، إضافةً للمسافة', 'nbFilter',
                def: false),
            if (store.b('nbFilter', false))
              Box(
                child: Column(children: [
                  Row(children: [
                    Expanded(
                      child: OutlinedButton(
                          onPressed: () => store.setNb(kNeighborhoods.toSet()),
                          child: const Text('تحديد الكل')),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                          onPressed: () => store.setNb(<String>{}),
                          child: const Text('إلغاء الكل')),
                    ),
                  ]),
                  for (final n in kNeighborhoods)
                    CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      activeColor: _gold(c),
                      title: Text(n,
                          style: TextStyle(color: _text(c), fontSize: 13)),
                      value: sel.contains(n),
                      onChanged: (v) {
                        final s = {...sel};
                        v == true ? s.add(n) : s.remove(n);
                        store.setNb(s);
                      },
                    ),
                ]),
              ),
            const Section('مظهر البرنامج'),
            Row(children: [
              for (final t in const [
                ['system', 'النظام'],
                ['light', '☀ نهاري'],
                ['dark', '☾ ليلي'],
              ])
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: SizedBox(
                          width: double.infinity,
                          child: Text(t[1], textAlign: TextAlign.center)),
                      selected: store.s('theme', 'system') == t[0],
                      selectedColor: _gold(c).withOpacity(.3),
                      onSelected: (_) => store.set('theme', t[0]),
                    ),
                  ),
                ),
            ]),
            const Section('تشخيص آخر الطلبات'),
            Box(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('آخر فحص: ${store.s('hb', 'ما صار فحص (الخدمة مو شغالة)')}',
                        style: TextStyle(color: _sub(c), fontSize: 12)),
                    const SizedBox(height: 6),
                    Text(store.s('diag', 'ما صار أي فحص لطلب لحد الآن'),
                        style: TextStyle(color: _sub(c), fontSize: 12)),
                    TextButton(
                        onPressed: store.load, child: const Text('تحديث')),
                  ]),
            ),
            const SizedBox(height: 18),
            FilledButton(
              style: FilledButton.styleFrom(
                  backgroundColor: _gold(c),
                  foregroundColor: const Color(0xFF17130A),
                  minimumSize: const Size.fromHeight(50)),
              onPressed: () {
                ScaffoldMessenger.of(c).showSnackBar(
                    const SnackBar(content: Text('تم حفظ الإعدادات')));
                Navigator.pop(c);
              },
              child: const Text('حفظ الإعدادات'),
            ),
            const SizedBox(height: 6),
            Center(
              child: Text('كل تغيير ينحفظ فورًا',
                  style: TextStyle(color: _sub(c), fontSize: 11)),
            ),
          ]),
        );
      },
    );
  }
}

// ---------------- الإحصائيات ----------------
class StatsPage extends StatefulWidget {
  const StatsPage({super.key});
  @override
  State<StatsPage> createState() => _StatsPageState();
}

class _StatsPageState extends State<StatsPage> {
  @override
  void initState() {
    super.initState();
    store.load();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (c, _) {
        final a = store.i('stat_accept'), r = store.i('stat_reject');
        return Scaffold(
          appBar: AppBar(title: const Text('الإحصائيات')),
          body: ListView(padding: const EdgeInsets.all(16), children: [
            Row(children: [
              Expanded(child: _Stat('مقبول', a, true)),
              const SizedBox(width: 10),
              Expanded(child: _Stat('متجاهل', r, false)),
            ]),
            Box(
              child: Center(
                child: Text('المجموع: ${a + r}',
                    style: TextStyle(
                        color: _text(c),
                        fontSize: 16,
                        fontWeight: FontWeight.bold)),
              ),
            ),
            OutlinedButton(
              onPressed: () async {
                await store.set('stat_accept', '0');
                await store.set('stat_reject', '0');
              },
              child: const Text('تصفير الإحصائيات'),
            ),
          ]),
        );
      },
    );
  }
}
