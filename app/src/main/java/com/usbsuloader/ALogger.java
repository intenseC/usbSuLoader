package com.usbsuloader;
import static com.usbsuloader.MainActivity.mService;

import android.content.Context;
import android.util.Log;

import com.google.cloud.logging.Severity;


public class ALogger {


    protected Context context;

    static protected String logLevel = "info";

    public ALogger() {
        if(mService == null) {
            Log.d("ALogger ERR", "no context");
            return;
        }
        this.context = mService.accessServiceContext();
    }

    protected enum LogVerbosity  {
        DEBUG,
        INFO,
        ERROR;
        public boolean isLessVerboseThan(LogVerbosity other) {
            return this.compareTo(other) < 0;
        }
    }

    static protected LogVerbosity verbosity = LogVerbosity.DEBUG;

    public static void logVerbosityLevel(Context context) {
        logLevel = //   PropertyReader.getProperties(context, "app.properties").getProperty("logLevel");
                "debug";
        if (logLevel != null && !logLevel.isEmpty()) {
            switch (logLevel) {
                case "debug":
                    verbosity = LogVerbosity.DEBUG;
                    break;

                case "info":
                    verbosity = LogVerbosity.INFO;
                    break;

                case "error":
                    verbosity = LogVerbosity.ERROR;
                    break;

                default:
            }
        }
    }

    public static void errorLog(Class<?> className, String message, Boolean writeToDisk, Boolean writeToCloud) {
        String classTag = className.getSimpleName();
        Log.e(classTag, message);
        if (writeToDisk) {
            if(mService == null) {
                Log.d("errorLog ERR", "no context");
                return;
            }
            toDisk(mService.accessServiceContext(), classTag, message);
        }
        if (writeToCloud) {
            toCloud(message, Severity.ERROR);
        }
    }

    public static void infoLog(Class<?> className, String message, Boolean writeToDisk, Boolean writeToCloud) {
        if(verbosity == LogVerbosity.ERROR) return;
        String classTag = className.getSimpleName();
        Log.i(classTag, message);
        if (writeToDisk) {
            if(mService == null) {
                Log.d("infoLog ERR", "no context");
                return;
            }
            toDisk(mService.accessServiceContext(), classTag, message);
        }
        if (writeToCloud) {
            toCloud(message, Severity.INFO);
        }
    }

    public static void debugLog(Class<?> className, String message, Boolean writeToDisk, Boolean writeToCloud) {
        if(verbosity == LogVerbosity.ERROR || verbosity == LogVerbosity.INFO) return;
        String classTag = className.getSimpleName();
        Log.d(classTag, message);
        if (writeToDisk) {
            if(mService == null) {
                Log.d("debugLog ERR", "no context");
                return;
            }
            toDisk(mService.accessServiceContext(), classTag, message);
        }
        if (writeToCloud) {
            toCloud(message, Severity.DEBUG);
        }
    }

    public static void toDisk(Context context, String classTag, String message) {
    //    DiskLogger.saveLogsToDisk(context, classTag + message);
    }

    public static void toCloud(String message, Severity severity) {
//        GoogleCloudLogger googleCloudLogger = GoogleCloudLogger.getInstance();
//        googleCloudLogger.log(message, severity);
    }

    /* ++========================================================================++  */
    // JNI Bridge Endpoint: Invoked by the platform-glue layer in C
    public static void logFromNative(int level, String tag, String message) {
    //    android.util.Log.e("JNI_BRIDGE_VERIFY", "LEVEL: " + level + " | TAG: " + tag + " | MSG: " + message);
        // Force disk writes for headless native telemetry, disable cloud sync by default
        boolean writeToDisk = true;
        boolean writeToCloud = false;

        // Wrap the incoming C tag so it stands out inside logcat/disk files
        String nativeMessage = "[" + tag + "] " + message;

        switch (level) {
            case 1: // ERROR
                errorLog(ALogger.class, nativeMessage, writeToDisk, writeToCloud);
                break;

            case 2: // WARNING
                // Mapping to infoLog since ALogger lacks a specific warning severity case
                infoLog(ALogger.class, "[WARN]" + nativeMessage, writeToDisk, writeToCloud);
                break;

            case 3: // INFO
                infoLog(ALogger.class, nativeMessage, writeToDisk, writeToCloud);
                break;

            case 4: // DEBUG
                debugLog(ALogger.class, nativeMessage, writeToDisk, writeToCloud);
                break;

            default:
                // Fallback for unexpected level definitions
                Log.w("ALogger Native", "Unknown log level (" + level + "): " + nativeMessage);
                break;
        }
    }

    /*  ++========================================================================++  */
}
