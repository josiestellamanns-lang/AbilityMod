package com.abilitymod;

/** Small text helpers shared by server and client. */
public final class Pretty {
    private Pretty() {}

    /** "minecraft:village_plains" -> "Village Plains", "mod:cool_ruin" -> "Cool Ruin (mod)". */
    public static String structure(String id) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return ns.equals("minecraft") ? sb.toString() : sb + " (" + ns + ")";
    }
}
