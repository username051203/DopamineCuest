import re

with open('app/src/main/java/com/dopaminequest/utils/AppState.java', 'r') as f:
    content = f.read()

# Replace isAllowed method
old_isallowed = '''    public static boolean isAllowed(Context ctx, String pkg) {
        if (pkg == null) return true;
        if (pkg.equals("com.dopaminequest")) return true;
        if (pkg.startsWith("com.android.systemui")) return true;
        return getAllowlist(ctx).contains(pkg);
    }'''

new_isallowed = '''    public static boolean isAllowed(Context ctx, String pkg) {
        if (pkg == null) return true;
        if (pkg.equals("com.dopaminequest")) return true;
        // System UI and input methods are ALWAYS allowed — never block them
        if (pkg.startsWith("com.android.systemui")) return true;
        if (pkg.startsWith("com.android.inputmethod")) return true;
        if (pkg.startsWith("com.google.android.inputmethod")) return true;
        if (pkg.startsWith("com.google.android.gboard")) return true;
        if (pkg.startsWith("com.touchtype.swiftkey")) return true;
        if (pkg.startsWith("com.swiftkey")) return true;
        if (pkg.startsWith("com.samsung.android.honeyboard")) return true;
        if (pkg.startsWith("com.sec.android.inputmethod")) return true;
        if (pkg.startsWith("com.miui.input")) return true;
        if (pkg.startsWith("com.baidu.input")) return true;
        if (pkg.startsWith("com.iflytek")) return true;
        // Check if it is the currently active IME
        try {
            String ime = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains(pkg)) return true;
        } catch (Exception ignored) {}
        return getAllowlist(ctx).contains(pkg);
    }'''

content = content.replace(old_isallowed, new_isallowed)

# Replace defaultSystemApps with expanded list
old_system = '''    private static Set<String> defaultSystemApps() {
        Set<String> s = new HashSet<>();
        s.add("com.dopaminequest");
        s.add("com.android.phone");
        s.add("com.android.dialer");
        s.add("com.google.android.dialer");
        s.add("com.android.contacts");
        s.add("com.android.mms");
        s.add("com.google.android.apps.messaging");
        s.add("com.android.settings");
        s.add("com.android.camera2");
        s.add("com.google.android.apps.camera");
        s.add("com.android.deskclock");
        s.add("com.google.android.deskclock");
        s.add("com.android.systemui");
        s.add("com.android.launcher3");
        s.add("com.google.android.apps.nexuslauncher");
        s.add("com.miui.home");
        s.add("com.sec.android.app.launcher");
        s.add("com.huawei.android.launcher");
        s.add("com.oppo.launcher");
        s.add("net.oneplus.launcher");
        s.add("com.android.emergency");
        s.add("com.google.android.gms");
        s.add("com.android.vending");
        return s;
    }'''

new_system = '''    private static Set<String> defaultSystemApps() {
        Set<String> s = new HashSet<>();
        // Our app
        s.add("com.dopaminequest");
        // Phone & messaging
        s.add("com.android.phone");
        s.add("com.android.dialer");
        s.add("com.google.android.dialer");
        s.add("com.android.contacts");
        s.add("com.android.mms");
        s.add("com.google.android.apps.messaging");
        // Settings
        s.add("com.android.settings");
        s.add("com.miui.securitycenter");
        s.add("com.samsung.android.settings");
        // Camera
        s.add("com.android.camera2");
        s.add("com.google.android.apps.camera");
        s.add("com.sec.android.app.camera");
        s.add("com.miui.camera");
        // Clock & calendar
        s.add("com.android.deskclock");
        s.add("com.google.android.deskclock");
        s.add("com.android.calendar");
        s.add("com.google.android.calendar");
        // System UI & launchers
        s.add("com.android.systemui");
        s.add("com.android.launcher3");
        s.add("com.google.android.apps.nexuslauncher");
        s.add("com.miui.home");
        s.add("com.sec.android.app.launcher");
        s.add("com.huawei.android.launcher");
        s.add("com.oppo.launcher");
        s.add("net.oneplus.launcher");
        s.add("com.realme.launcher");
        s.add("com.vivo.launcher");
        // Keyboards — comprehensive list
        s.add("com.android.inputmethod.latin");
        s.add("com.google.android.inputmethod.latin");
        s.add("com.google.android.gboard");
        s.add("com.touchtype.swiftkey");
        s.add("com.swiftkey.swiftkeyapp");
        s.add("com.samsung.android.honeyboard");
        s.add("com.sec.android.inputmethod");
        s.add("com.miui.input.touchpal");
        s.add("com.miui.inputmethod");
        s.add("com.baidu.input");
        s.add("com.iflytek.inputmethod");
        s.add("com.nuance.swype.dtc");
        s.add("com.grammarly.keyboard");
        s.add("com.fleksy.app");
        s.add("com.keenan.swype");
        s.add("com.syntellia.fleksy.keyboard");
        s.add("com.lge.ime");
        s.add("jp.co.omronsoft.openwnn");
        s.add("com.motorola.android.inputmethod");
        // Essential Google services
        s.add("com.android.emergency");
        s.add("com.google.android.gms");
        s.add("com.android.vending");
        s.add("com.google.android.gsf");
        s.add("com.google.android.packageinstaller");
        s.add("com.android.packageinstaller");
        // File manager (needed to install APKs)
        s.add("com.android.documentsui");
        s.add("com.google.android.documentsui");
        s.add("com.mi.android.globalFileexplorer");
        s.add("com.samsung.android.myfiles");
        return s;
    }'''

content = content.replace(old_system, new_system)

with open('app/src/main/java/com/dopaminequest/utils/AppState.java', 'w') as f:
    f.write(content)

print("Done")

