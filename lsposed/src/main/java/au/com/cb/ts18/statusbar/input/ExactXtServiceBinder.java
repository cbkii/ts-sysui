package au.com.cb.ts18.statusbar.input;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Minimal Binder projection of the exact recovered XTService interface. The full
 * proprietary AIDL is deliberately not copied into the product.
 */
final class ExactXtServiceBinder {
    private static final String COMMAND_DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandAidl";
    private static final String CALLBACK_DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandCallbackAidl";

    // Exact AIDL ordinals from the supplied 67-method interface. FIRST_CALL_TRANSACTION
    // is ordinal 1, therefore the zero-based Binder offsets below are intentional.
    private static final int TX_GET_REVERSE_STATUS = IBinder.FIRST_CALL_TRANSACTION + 16;
    private static final int TX_GET_SLEEP_STATUS = IBinder.FIRST_CALL_TRANSACTION + 17;
    private static final int TX_MEDIA_NEXT = IBinder.FIRST_CALL_TRANSACTION + 27;
    private static final int TX_MEDIA_PAUSE = IBinder.FIRST_CALL_TRANSACTION + 28;
    private static final int TX_MEDIA_PLAY = IBinder.FIRST_CALL_TRANSACTION + 29;
    private static final int TX_MEDIA_PRE = IBinder.FIRST_CALL_TRANSACTION + 30;
    private static final int TX_REGISTER_COMMAND_CALLBACK = IBinder.FIRST_CALL_TRANSACTION + 48;
    private static final int TX_UNREGISTER_COMMAND_CALLBACK = IBinder.FIRST_CALL_TRANSACTION + 55;

    private static final int CB_BT_CALL = IBinder.FIRST_CALL_TRANSACTION;
    private static final int CB_BT_CONNECTED = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int CB_BT_PHONE = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int CB_EXTENDED = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final int CB_REVERSE = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final int CB_SLEEP = IBinder.FIRST_CALL_TRANSACTION + 5;
    private static final int CB_SYSTEM_VOLUME = IBinder.FIRST_CALL_TRANSACTION + 6;
    private static final int CB_VOLUME_STATUS = IBinder.FIRST_CALL_TRANSACTION + 7;

    private ExactXtServiceBinder() {}

    static void registerCallback(IBinder remote, IBinder callback) throws RemoteException {
        transactBinder(remote, TX_REGISTER_COMMAND_CALLBACK, callback);
    }

    static void unregisterCallback(IBinder remote, IBinder callback) throws RemoteException {
        transactBinder(remote, TX_UNREGISTER_COMMAND_CALLBACK, callback);
    }

    static void requestInitialState(IBinder remote) throws RemoteException {
        transactNoArgs(remote, TX_GET_REVERSE_STATUS);
        transactNoArgs(remote, TX_GET_SLEEP_STATUS);
    }

    static void qualifyMedia(IBinder remote, String action) throws RemoteException {
        switch (action == null ? "" : action) {
            case "previous": transactNoArgs(remote, TX_MEDIA_PRE); return;
            case "play": transactNoArgs(remote, TX_MEDIA_PLAY); return;
            case "pause": transactNoArgs(remote, TX_MEDIA_PAUSE); return;
            case "next": transactNoArgs(remote, TX_MEDIA_NEXT); return;
            default: throw new IllegalArgumentException("unsupported qualification action");
        }
    }

    static boolean isQualificationAction(String action) {
        return "previous".equals(action) || "play".equals(action)
                || "pause".equals(action) || "next".equals(action);
    }

    private static void transactNoArgs(IBinder remote, int code) throws RemoteException {
        if (remote == null) throw new RemoteException("XTService binder unavailable");
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(COMMAND_DESCRIPTOR);
            boolean handled = remote.transact(code, data, reply, 0);
            if (!handled) throw new RemoteException("XTService rejected transaction " + code);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void transactBinder(IBinder remote, int code, IBinder callback)
            throws RemoteException {
        if (remote == null) throw new RemoteException("XTService binder unavailable");
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(COMMAND_DESCRIPTOR);
            data.writeStrongBinder(callback);
            boolean handled = remote.transact(code, data, reply, 0);
            if (!handled) throw new RemoteException("XTService rejected transaction " + code);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    interface Listener {
        void onReverseStatus(int status);
        void onSleepStatus(int status);
    }

    static final class CallbackBinder extends Binder {
        private final Listener listener;

        CallbackBinder(Listener listener) {
            this.listener = listener;
            attachInterface(null, CALLBACK_DESCRIPTOR);
        }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CALLBACK_DESCRIPTOR);
                return true;
            }
            switch (code) {
                case CB_BT_CALL:
                    data.enforceInterface(CALLBACK_DESCRIPTOR);
                    data.readInt(); data.readString(); data.readString();
                    return success(reply);
                case CB_BT_CONNECTED:
                case CB_BT_PHONE:
                case CB_SYSTEM_VOLUME:
                case CB_VOLUME_STATUS:
                    data.enforceInterface(CALLBACK_DESCRIPTOR);
                    data.readInt();
                    return success(reply);
                case CB_EXTENDED:
                    data.enforceInterface(CALLBACK_DESCRIPTOR);
                    if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data);
                    return success(reply);
                case CB_REVERSE:
                    data.enforceInterface(CALLBACK_DESCRIPTOR);
                    if (listener != null) listener.onReverseStatus(data.readInt());
                    return success(reply);
                case CB_SLEEP:
                    data.enforceInterface(CALLBACK_DESCRIPTOR);
                    if (listener != null) listener.onSleepStatus(data.readInt());
                    return success(reply);
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static boolean success(Parcel reply) {
            if (reply != null) reply.writeNoException();
            return true;
        }
    }
}
