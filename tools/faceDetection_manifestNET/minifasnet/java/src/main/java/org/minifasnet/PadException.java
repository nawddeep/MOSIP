package org.minifasnet;

/** Every input/model problem is reported with a typed code; nothing fails silently. */
public class PadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Code { INVALID_FRAME, INVALID_BOX, FACE_TOO_SMALL, MODEL_LOAD, INFERENCE }

    private final Code code;

    public PadException(Code code, String message) { super(message); this.code = code; }
    public PadException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }

    public Code getCode() { return code; }
}
