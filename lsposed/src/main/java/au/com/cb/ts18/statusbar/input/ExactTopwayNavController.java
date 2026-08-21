package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns one reversible media group in the exact weighted Topway navbar host. */
final class ExactTopwayNavController {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE = "au.com.cb.ts18.statusbar.input";
    private static final long CONFIGURATION_POLL_MS = 2500L;
    private static final Object OWNER = new Object();
    private static final String[] STOCK_IDS = {
            "navbar_guanping", "home", "back", "recent_apps", "app",
            "navbar_volume_plus", "navbar_volume_reduce"
    };

    private static Binding current;

    private ExactTopwayNavController() {}

    static synchronized void onInflated(View root) {
        if (root == null || !NavFeatureRuntime.isOperational()) return;
        if (current != null && current.root == root) {
            current.reconcileSoon();
            return;
        }
        if (current != null) current.detach();
        current = new Binding(root);
        current.attach();
    }

    static void failOpen() {
        Binding target;
        synchronized (ExactTopwayNavController.class) {
            target = current;
            current = null;
        }
        if (target == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.detach();
        } else {
            new Handler(Looper.getMainLooper()).post(target::detach);
        }
    }

    private static final class Binding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final View root;
        final Map<NavAction, ImageButton> buttons = new EnumMap<>(NavAction.class);
        LinearLayout host;
        LinearLayout ownedGroup;
        List<NavAction> ownedActions = Collections.emptyList();
        NavMediaSessionRepository media;
        boolean reconciling;
        boolean detached;
        String lastStopReason = "";
        final Runnable reconcileRunnable = this::reconcile;
        final Runnable configurationPoll = this::reconcile;

        Binding(View root) {
            this.root = root;
        }

        void attach() {
            root.addOnAttachStateChangeListener(this);
            root.addOnLayoutChangeListener(this);
            ExactSystemUiIdentity.start(root.getContext());
            ExactSystemUiIdentity.whenResolved(this::reconcileSoon);
            reconcileSoon();
        }

        void detach() {
            if (detached) return;
            detached = true;
            root.removeOnAttachStateChangeListener(this);
            root.removeOnLayoutChangeListener(this);
            root.removeCallbacks(reconcileRunnable);
            root.removeCallbacks(configurationPoll);
            removeOwnedGroup();
            NavHierarchyProbe.detach(root);
            if (NavBarState.root() == root) NavBarState.clear();
        }

        @Override public void onViewAttachedToWindow(View view) {
            reconcileSoon();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            root.removeCallbacks(reconcileRunnable);
            root.removeCallbacks(configurationPoll);
            removeOwnedGroup();
            NavHierarchyProbe.detach(root);
        }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                             int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                reconcileSoon();
            }
        }

        void reconcileSoon() {
            if (detached) return;
            root.removeCallbacks(reconcileRunnable);
            root.post(reconcileRunnable);
        }

        void reconcile() {
            if (detached || reconciling || !NavFeatureRuntime.isOperational()) return;
            reconciling = true;
            NavConfig.Snapshot config = null;
            try {
                NavBarState.Capture capture = NavBarState.capture(root, root.getLayoutParams());
                config = NavConfig.get(root.getContext());
                if (config.probeEnabled) {
                    NavHierarchyProbe.sync(root, capture.generation);
                } else {
                    NavHierarchyProbe.detach(root);
                }

                if (!config.enabled || config.actions.isEmpty()) {
                    removeOwnedGroup();
                    return;
                }
                if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0) {
                    removeOwnedGroup();
                    stop("root-not-laid-out", config.debug);
                    return;
                }
                if (!ExactSystemUiIdentity.isSupported()) {
                    removeOwnedGroup();
                    stop("identity-" + ExactSystemUiIdentity.state() + '-'
                            + ExactSystemUiIdentity.detail(), config.debug);
                    return;
                }

                Preflight preflight = preflight(config);
                if (!preflight.safe) {
                    removeOwnedGroup();
                    stop(preflight.reason, config.debug);
                    return;
                }
                if (ownedGroup != null && ownedGroup.getParent() == preflight.host
                        && ownedActions.equals(config.actions)) {
                    host = preflight.host;
                    return;
                }

                removeOwnedGroup();
                inject(preflight, config);
                lastStopReason = "";
                RateLimitedLog.always("exact right-nav media group active: actions="
                        + actionIds(config.actions) + " projectedCellPx="
                        + preflight.policy.projectedCellPx + " minimumPx="
                        + preflight.policy.minimumTouchPx);
            } catch (Throwable t) {
                removeOwnedGroup();
                NavFeatureRuntime.recordFailure("reconcile", t);
            } finally {
                reconciling = false;
                root.removeCallbacks(configurationPoll);
                if (!detached && config != null
                        && (config.enabled || config.probeEnabled)) {
                    // Settings.Global is intentionally observed only while a nav
                    // feature is armed. This makes disablement converge without
                    // a permanent SystemUI poll when the feature is off.
                    root.postDelayed(configurationPoll, CONFIGURATION_POLL_MS);
                }
            }
        }

        private Preflight preflight(NavConfig.Snapshot config) {
            Resources resources = root.getResources();
            int hostId = resources.getIdentifier("navbar_left", "id", SYSTEM_UI_PACKAGE);
            if (hostId == 0) return Preflight.failure("missing-navbar_left-id");
            View candidate = root.findViewById(hostId);
            if (!(candidate instanceof LinearLayout)) {
                return Preflight.failure("navbar_left-not-linear-layout");
            }
            LinearLayout exactHost = (LinearLayout) candidate;
            if (exactHost.getOrientation() != LinearLayout.VERTICAL) {
                return Preflight.failure("navbar_left-not-vertical");
            }
            if (exactHost.getWeightSum() > 0f) {
                return Preflight.failure("explicit-host-weight-sum");
            }

            Set<Integer> requiredIds = new HashSet<>();
            List<Float> visibleWeights = new ArrayList<>();
            int insertionIndex = Integer.MAX_VALUE;
            for (String name : STOCK_IDS) {
                int id = resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE);
                if (id == 0 || !requiredIds.add(id)) {
                    return Preflight.failure("missing-or-duplicate-stock-id-" + name);
                }
                View child = exactHost.findViewById(id);
                if (child == null || child.getParent() != exactHost) {
                    return Preflight.failure("stock-child-not-direct-" + name);
                }
                ViewGroup.LayoutParams raw = child.getLayoutParams();
                if (!(raw instanceof LinearLayout.LayoutParams)) {
                    return Preflight.failure("stock-layout-params-" + name);
                }
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) raw;
                if (params.height != 0 || params.weight <= 0f
                        || Float.isNaN(params.weight) || Float.isInfinite(params.weight)) {
                    return Preflight.failure("stock-not-weighted-" + name);
                }
                if (child.getVisibility() != View.GONE) visibleWeights.add(params.weight);
                if ("navbar_volume_plus".equals(name)
                        || "navbar_volume_reduce".equals(name)) {
                    insertionIndex = Math.min(insertionIndex, exactHost.indexOfChild(child));
                }
            }

            Set<Integer> seenDirectIds = new HashSet<>();
            for (int i = 0; i < exactHost.getChildCount(); i++) {
                View child = exactHost.getChildAt(i);
                if (child == ownedGroup && isOwned(child)) continue;
                if (!requiredIds.contains(child.getId())
                        || !seenDirectIds.add(child.getId())) {
                    return Preflight.failure("unknown-or-duplicate-direct-child");
                }
            }
            if (!seenDirectIds.equals(requiredIds)) {
                return Preflight.failure("missing-direct-child");
            }
            if (insertionIndex == Integer.MAX_VALUE) {
                return Preflight.failure("volume-anchor-missing");
            }
            if (ownedGroup != null && ownedGroup.getParent() == exactHost
                    && exactHost.indexOfChild(ownedGroup) < insertionIndex) {
                // Preflight runs before an action-change reinjection. Convert the
                // live index to the post-removal stock index.
                insertionIndex--;
            }

            int availableWidth = exactHost.getWidth()
                    - exactHost.getPaddingLeft() - exactHost.getPaddingRight();
            int availableHeight = exactHost.getHeight()
                    - exactHost.getPaddingTop() - exactHost.getPaddingBottom();
            TopwayWeightedNavPolicy.Result policy = TopwayWeightedNavPolicy.evaluate(
                    availableWidth, availableHeight,
                    exactHost.getResources().getDisplayMetrics().density,
                    visibleWeights, config.actions.size(), config.minTouchDp);
            if (!policy.safe) {
                return Preflight.failure("weighted-policy-" + policy.failureReason);
            }
            return Preflight.success(exactHost, insertionIndex, policy);
        }

        private void inject(Preflight preflight, NavConfig.Snapshot config) throws Exception {
            Context moduleContext = root.getContext().createPackageContext(
                    MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            LinearLayout group = new LinearLayout(root.getContext());
            group.setOrientation(LinearLayout.VERTICAL);
            group.setId(View.generateViewId());
            group.setTag(R.id.ts18_nav_owner_tag, OWNER);
            group.setClipChildren(false);
            group.setClipToPadding(false);
            group.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0,
                    preflight.policy.mediaGroupWeight));

            buttons.clear();
            for (NavAction action : config.actions) {
                ImageButton button = createButton(moduleContext, action);
                group.addView(button, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                buttons.put(action, button);
            }
            preflight.host.addView(group, preflight.insertionIndex);
            host = preflight.host;
            ownedGroup = group;
            ownedActions = Collections.unmodifiableList(new ArrayList<>(config.actions));

            media = new NavMediaSessionRepository(root.getContext(), ownedActions,
                    this::applySnapshot);
            applySnapshot(NavMediaSessionRepository.Snapshot.empty());
            media.start();
        }

        private ImageButton createButton(Context moduleContext, NavAction action) {
            ImageButton button = new ImageButton(root.getContext());
            button.setId(View.generateViewId());
            button.setTag(R.id.ts18_nav_owner_tag, OWNER);
            TypedValue selectable = new TypedValue();
            if (root.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, selectable, true)
                    && selectable.resourceId != 0) {
                button.setBackgroundResource(selectable.resourceId);
            } else {
                button.setBackgroundColor(Color.TRANSPARENT);
            }
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int padding = Math.max(0, Math.round(8f
                    * root.getResources().getDisplayMetrics().density));
            button.setPadding(padding, padding, padding, padding);
            button.setImageDrawable(drawable(moduleContext, iconFor(action, false)));
            button.setContentDescription(moduleContext.getString(labelFor(action)));
            button.setEnabled(false);
            button.setAlpha(0.38f);
            button.setOnClickListener(view -> {
                NavMediaSessionRepository repository = media;
                if (repository != null && view.isEnabled()) repository.dispatch(action);
            });
            return button;
        }

        private void applySnapshot(NavMediaSessionRepository.Snapshot snapshot) {
            if (detached || ownedGroup == null || snapshot == null) return;
            try {
                Context moduleContext = root.getContext().createPackageContext(
                        MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
                for (Map.Entry<NavAction, ImageButton> entry : buttons.entrySet()) {
                    NavAction action = entry.getKey();
                    ImageButton button = entry.getValue();
                    boolean enabled = snapshot.enabled(action);
                    button.setEnabled(enabled);
                    button.setAlpha(enabled ? 1f : 0.38f);
                    if (action == NavAction.PLAY_PAUSE) {
                        button.setImageDrawable(drawable(moduleContext,
                                iconFor(action, snapshot.playing)));
                        button.setContentDescription(moduleContext.getString(
                                snapshot.playing ? R.string.ts18_nav_pause
                                        : R.string.ts18_nav_play));
                    }
                }
            } catch (Throwable t) {
                NavFeatureRuntime.recordFailure("presentation", t);
            }
        }

        private void removeOwnedGroup() {
            NavMediaSessionRepository repository = media;
            media = null;
            if (repository != null) repository.stop();
            LinearLayout group = ownedGroup;
            ownedGroup = null;
            ownedActions = Collections.emptyList();
            buttons.clear();
            if (group != null && isOwned(group) && group.getParent() instanceof ViewGroup) {
                ((ViewGroup) group.getParent()).removeView(group);
            }
            host = null;
        }

        private void stop(String reason, boolean debug) {
            if (!reason.equals(lastStopReason)) {
                lastStopReason = reason;
                RateLimitedLog.always("right-nav kept stock: " + reason);
            } else {
                RateLimitedLog.debug(debug, "right-nav kept stock: " + reason);
            }
        }
    }

    private static boolean isOwned(View view) {
        return view != null && view.getTag(R.id.ts18_nav_owner_tag) == OWNER;
    }

    private static Drawable drawable(Context moduleContext, int id) {
        return moduleContext.getDrawable(id);
    }

    private static int iconFor(NavAction action, boolean playing) {
        switch (action) {
            case PREVIOUS: return R.drawable.ic_ts18_media_previous;
            case PLAY_PAUSE:
                return playing ? R.drawable.ic_ts18_media_pause : R.drawable.ic_ts18_media_play;
            case NEXT: return R.drawable.ic_ts18_media_next;
            default: throw new IllegalArgumentException("unsupported nav action");
        }
    }

    private static int labelFor(NavAction action) {
        switch (action) {
            case PREVIOUS: return R.string.ts18_nav_previous;
            case PLAY_PAUSE: return R.string.ts18_nav_play;
            case NEXT: return R.string.ts18_nav_next;
            default: throw new IllegalArgumentException("unsupported nav action");
        }
    }

    private static String actionIds(List<NavAction> actions) {
        StringBuilder out = new StringBuilder();
        for (NavAction action : actions) {
            if (out.length() > 0) out.append(',');
            out.append(action.id());
        }
        return out.toString();
    }

    private static final class Preflight {
        final boolean safe;
        final String reason;
        final LinearLayout host;
        final int insertionIndex;
        final TopwayWeightedNavPolicy.Result policy;

        private Preflight(boolean safe, String reason, LinearLayout host,
                          int insertionIndex, TopwayWeightedNavPolicy.Result policy) {
            this.safe = safe;
            this.reason = reason;
            this.host = host;
            this.insertionIndex = insertionIndex;
            this.policy = policy;
        }

        static Preflight failure(String reason) {
            return new Preflight(false, reason, null, -1, null);
        }

        static Preflight success(LinearLayout host, int insertionIndex,
                                 TopwayWeightedNavPolicy.Result policy) {
            return new Preflight(true, "", host, insertionIndex, policy);
        }
    }
}
