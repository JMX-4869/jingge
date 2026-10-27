package com.xiaojing.jingge;

/** 一套配色。id 以 bi: 开头是内置，c: 开头是自制。 */
final class Theme {
    final String id;
    final String name;
    final int bg;
    final int panel;
    final int line;
    final int text;
    final int muted;
    final int accent;

    Theme(String id, String name, int bg, int panel, int line, int text, int muted, int accent) {
        this.id = id;
        this.name = name;
        this.bg = bg;
        this.panel = panel;
        this.line = line;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
    }

    boolean custom() {
        return id != null && id.startsWith("c:");
    }
}
