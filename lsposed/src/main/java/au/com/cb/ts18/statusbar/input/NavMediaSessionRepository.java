package au.com.cb.ts18.statusbar.input;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded client of existing SystemUI-visible media sessions. */
final class NavMediaSessionRepository {
    interface Listener {
        void onMediaStateChanged(Snapshot snapshot);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread workerThread = new HandlerThread("TS18-NavMedia");
    private final Handler workerHandler;
    private final MediaSessionManager sessionManager;
    private final List<NavAction> configuredActions;
    private final Listener listener;

    private volatile boolean stopped;
    private volatile MediaController selected;
    private volatile Snapshot snapshot = Snapshot.empty();

    private final MediaSessionManager.OnActiveSessionsChangedListener activeListener =
            controllers -> {
                try {
                    select(controllers == null ? Collections.emptyList() : controllers);
                } catch (Throwable t) {
                    NavFeatureRuntime.recordFailure("media-active-sessions", t);
                }
            };
    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            try {
                refresh();
            } catch (Throwable t) {
                NavFeatureRuntime.recordFailure("media-playback-callback", t);
            }
        }

        @Override public void onSessionDestroyed() {
            refresh();
        }
    };

    NavMediaSessionRepository(Context context,
                              List<NavAction> configuredActions,
                              Listener listener) {
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        sessionManager = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        this.configuredActions = Collections.unmodifiableList(
                new ArrayList<>(configuredActions));
        this.listener = listener;
    }

    void start() {
        if (sessionManager == null || stopped) {
            publish(null, null, 0);
            return;
        }
        workerHandler.post(this::startOnWorker);
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        snapshot = Snapshot.empty();
        mainHandler.removeCallbacksAndMessages(null);
        workerHandler.post(this::stopOnWorker);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    void dispatch(NavAction action) {
        if (stopped || action == null) return;
        workerHandler.post(() -> dispatchOnWorker(action));
    }

    private void refresh() {
        if (stopped || sessionManager == null) return;
        workerHandler.post(() -> {
            if (stopped) return;
            try {
                select(sessionManager.getActiveSessions(null));
            } catch (Throwable t) {
                NavFeatureRuntime.recordFailure("media-refresh", t);
            }
        });
    }

    private void startOnWorker() {
        if (stopped || sessionManager == null) return;
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    activeListener, (ComponentName) null, workerHandler);
            select(sessionManager.getActiveSessions(null));
        } catch (Throwable t) {
            if (!stopped) NavFeatureRuntime.recordFailure("media-start", t);
        }
    }

    private void stopOnWorker() {
        try {
            if (sessionManager != null) {
                sessionManager.removeOnActiveSessionsChangedListener(activeListener);
            }
        } catch (Throwable t) {
            RateLimitedLog.error("nav-media-listener-remove",
                    "failed to remove active-session listener", t);
        }
        MediaController previous = selected;
        selected = null;
        if (previous != null) {
            try {
                previous.unregisterCallback(controllerCallback);
            } catch (Throwable t) {
                RateLimitedLog.error("nav-media-callback-remove",
                        "failed to unregister selected controller callback", t);
            }
        }
        workerThread.quitSafely();
    }

    private void select(List<MediaController> controllers) {
        if (stopped) return;
        List<MediaController> active = controllers == null
                ? Collections.emptyList() : controllers;
        MediaController current = selected;
        int currentIndex = indexOfToken(active, current);
        List<NavMediaSelectionPolicy.Candidate> candidates = new ArrayList<>(active.size());
        for (MediaController controller : active) {
            PlaybackState state = controller == null ? null : controller.getPlaybackState();
            candidates.add(new NavMediaSelectionPolicy.Candidate(
                    selectionPlayback(state), isUsable(state)));
        }

        int selectedIndex = NavMediaSelectionPolicy.choose(candidates, currentIndex);
        MediaController next = selectedIndex >= 0 && selectedIndex < active.size()
                ? active.get(selectedIndex) : null;
        if (!sameToken(current, next)) {
            if (current != null) {
                try {
                    current.unregisterCallback(controllerCallback);
                } catch (Throwable t) {
                    RateLimitedLog.error("nav-media-callback-switch",
                            "failed to unregister old media callback", t);
                }
            }
            selected = next;
            if (next != null) {
                try {
                    next.registerCallback(controllerCallback, workerHandler);
                } catch (Throwable t) {
                    selected = null;
                    NavFeatureRuntime.recordFailure("media-callback-register", t);
                }
            }
        }
        MediaController chosen = selected;
        publish(chosen, chosen == null ? null : chosen.getPlaybackState(), active.size());
    }

    private void dispatchOnWorker(NavAction action) {
        if (stopped) return;
        try {
            MediaController controller = selected;
            if (controller == null) return;
            PlaybackState state = controller.getPlaybackState();
            long actions = state == null ? 0L : state.getActions();
            NavMediaDispatchPolicy.Command command = NavMediaDispatchPolicy.decide(
                    action,
                    dispatchPlayback(state),
                    supports(actions, PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                    supports(actions, PlaybackState.ACTION_PLAY)
                            || supports(actions, PlaybackState.ACTION_PLAY_PAUSE),
                    supports(actions, PlaybackState.ACTION_PAUSE)
                            || supports(actions, PlaybackState.ACTION_PLAY_PAUSE),
                    supports(actions, PlaybackState.ACTION_SKIP_TO_NEXT));
            if (command == NavMediaDispatchPolicy.Command.NONE) return;

            MediaController.TransportControls controls = controller.getTransportControls();
            switch (command) {
                case PREVIOUS:
                    controls.skipToPrevious();
                    break;
                case PLAY:
                    controls.play();
                    break;
                case PAUSE:
                    controls.pause();
                    break;
                case NEXT:
                    controls.skipToNext();
                    break;
                case NONE:
                default:
                    return;
            }
        } catch (Throwable t) {
            NavFeatureRuntime.recordFailure("media-dispatch", t);
        }
    }

    private boolean isUsable(PlaybackState state) {
        if (state == null) return false;
        long actions = state.getActions();
        for (NavAction action : configuredActions) {
            if (NavMediaDispatchPolicy.decide(
                    action,
                    dispatchPlayback(state),
                    supports(actions, PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                    supports(actions, PlaybackState.ACTION_PLAY)
                            || supports(actions, PlaybackState.ACTION_PLAY_PAUSE),
                    supports(actions, PlaybackState.ACTION_PAUSE)
                            || supports(actions, PlaybackState.ACTION_PLAY_PAUSE),
                    supports(actions, PlaybackState.ACTION_SKIP_TO_NEXT))
                    != NavMediaDispatchPolicy.Command.NONE) return true;
        }
        return false;
    }

    private void publish(MediaController controller, PlaybackState state, int controllerCount) {
        Snapshot next = Snapshot.from(controller, state, controllerCount);
        snapshot = next;
        if (listener != null && !stopped) {
            mainHandler.post(() -> {
                if (!stopped) listener.onMediaStateChanged(next);
            });
        }
    }

    private static int indexOfToken(List<MediaController> controllers, MediaController target) {
        if (target == null) return -1;
        for (int i = 0; i < controllers.size(); i++) {
            if (sameToken(target, controllers.get(i))) return i;
        }
        return -1;
    }

    private static boolean sameToken(MediaController first, MediaController second) {
        if (first == second) return true;
        return first != null && second != null
                && first.getSessionToken().equals(second.getSessionToken());
    }

    private static boolean supports(long actions, long action) {
        return (actions & action) != 0L;
    }

    private static NavMediaSelectionPolicy.Playback selectionPlayback(PlaybackState state) {
        if (state == null) return NavMediaSelectionPolicy.Playback.NONE;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
                return NavMediaSelectionPolicy.Playback.PLAYING;
            case PlaybackState.STATE_PAUSED:
                return NavMediaSelectionPolicy.Playback.PAUSED;
            case PlaybackState.STATE_STOPPED:
                return NavMediaSelectionPolicy.Playback.STOPPED;
            default:
                return NavMediaSelectionPolicy.Playback.OTHER;
        }
    }

    private static NavMediaDispatchPolicy.Playback dispatchPlayback(PlaybackState state) {
        if (state == null) return NavMediaDispatchPolicy.Playback.NONE;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
                return NavMediaDispatchPolicy.Playback.PLAYING;
            case PlaybackState.STATE_PAUSED:
                return NavMediaDispatchPolicy.Playback.PAUSED;
            case PlaybackState.STATE_STOPPED:
                return NavMediaDispatchPolicy.Playback.STOPPED;
            default:
                return NavMediaDispatchPolicy.Playback.OTHER;
        }
    }

    static final class Snapshot {
        final int controllerCount;
        final String selectedPackage;
        final int playbackState;
        final long actionBits;
        final boolean hasController;
        final boolean previousEnabled;
        final boolean playPauseEnabled;
        final boolean nextEnabled;
        final boolean playing;

        Snapshot(int controllerCount, String selectedPackage, int playbackState,
                 long actionBits, boolean hasController,
                 boolean previousEnabled, boolean playPauseEnabled,
                 boolean nextEnabled, boolean playing) {
            this.controllerCount = controllerCount;
            this.selectedPackage = selectedPackage == null ? "" : selectedPackage;
            this.playbackState = playbackState;
            this.actionBits = actionBits;
            this.hasController = hasController;
            this.previousEnabled = previousEnabled;
            this.playPauseEnabled = playPauseEnabled;
            this.nextEnabled = nextEnabled;
            this.playing = playing;
        }

        boolean enabled(NavAction action) {
            if (action == null) return false;
            switch (action) {
                case PREVIOUS: return previousEnabled;
                case PLAY_PAUSE: return playPauseEnabled;
                case NEXT: return nextEnabled;
                default: return false;
            }
        }

        static Snapshot from(MediaController controller, PlaybackState state,
                             int controllerCount) {
            if (controller == null || state == null) {
                return new Snapshot(Math.max(0, controllerCount), "",
                        state == null ? PlaybackState.STATE_NONE : state.getState(),
                        state == null ? 0L : state.getActions(),
                        false, false, false, false, false);
            }
            long actions = state.getActions();
            NavMediaDispatchPolicy.Playback playback = dispatchPlayback(state);
            return new Snapshot(
                    Math.max(0, controllerCount), controller.getPackageName(),
                    state.getState(), actions, true,
                    NavMediaDispatchPolicy.decide(NavAction.PREVIOUS, playback,
                            supports(actions, PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                            false, false, false) != NavMediaDispatchPolicy.Command.NONE,
                    NavMediaDispatchPolicy.decide(NavAction.PLAY_PAUSE, playback,
                            false,
                            supports(actions, PlaybackState.ACTION_PLAY)
                                    || supports(actions, PlaybackState.ACTION_PLAY_PAUSE),
                            supports(actions, PlaybackState.ACTION_PAUSE)
                                    || supports(actions, PlaybackState.ACTION_PLAY_PAUSE),
                            false) != NavMediaDispatchPolicy.Command.NONE,
                    NavMediaDispatchPolicy.decide(NavAction.NEXT, playback,
                            false, false, false,
                            supports(actions, PlaybackState.ACTION_SKIP_TO_NEXT))
                            != NavMediaDispatchPolicy.Command.NONE,
                    state.getState() == PlaybackState.STATE_PLAYING);
        }

        static Snapshot empty() {
            return new Snapshot(0, "", PlaybackState.STATE_NONE, 0L,
                    false, false, false, false, false);
        }
    }
}
