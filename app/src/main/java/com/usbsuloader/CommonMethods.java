package com.usbsuloader;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CommonMethods {
    public static final int REQUEST_CODE = 22;

    public static String parseException(Exception ex) {
        StringWriter errors = new StringWriter();
        ex.printStackTrace(new PrintWriter(errors));
        return errors.toString();
    }

}
