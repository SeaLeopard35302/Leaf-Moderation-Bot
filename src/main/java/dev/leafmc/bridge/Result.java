package dev.leafmc.bridge;

public record Result(boolean ok, String title, String detail) {
    public static Result ok(String t, String d) {
        return new Result(true, t, d);
    }

    public static Result fail(String t, String d) {
        return new Result(false, t, d);
    }
}
