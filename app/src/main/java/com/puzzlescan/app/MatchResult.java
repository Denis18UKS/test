package com.puzzlescan.app;

public class MatchResult {
    public boolean pieceDetected;
    public boolean found;
    public boolean ambiguous;
    public double confidence;
    public String method = "—";
    public double rotationDeg = 0.0;
    public float[] polygon;
    public double centerXPercent;
    public double centerYPercent;
    public String message = "";

    public static MatchResult noPiece(String message) {
        MatchResult r = new MatchResult();
        r.pieceDetected = false;
        r.message = message;
        return r;
    }

    public static MatchResult noMatch(String message) {
        MatchResult r = new MatchResult();
        r.pieceDetected = true;
        r.found = false;
        r.message = message;
        return r;
    }
}
