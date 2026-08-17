package au.com.cb.ts18.statusbar.input;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

final class HookRegistry {
    private final List<XC_MethodHook.Unhook> handles = new ArrayList<>();

    void addRequired(String name, XC_MethodHook.Unhook registered) {
        if (registered == null) {
            throw new IllegalStateException("no method hooked for " + name);
        }
        handles.add(registered);
    }

    int size() { return handles.size(); }

    void unhookAll() {
        for (int i = handles.size() - 1; i >= 0; i--) {
            try {
                handles.get(i).unhook();
            } catch (Throwable t) {
                RateLimitedLog.error("install-rollback", "failed to unhook partial installation", t);
            }
        }
        handles.clear();
    }
}
