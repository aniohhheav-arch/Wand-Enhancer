package com.stickyfingers.entity;

/** Behaviour/visual family of a zipper. */
public enum ZipperKind {
    SURFACE(true, false),
    DOOR(true, true),
    STORAGE(false, false),
    TRAVEL(true, true),
    MOB(false, false),
    CHAIN(false, false),
    AERIAL(true, true),
    TRAP(false, false),
    ESCAPE(true, false),
    SLICE(false, false),
    WALL(true, true),
    FLOOR(true, true),
    BODY(false, false),
    FIELD(false, false);

    public static final ZipperKind[] VALUES = values();

    /** Can act as a portal endpoint when linked. */
    public final boolean portal;
    /** Stays in the world (closed) after being closed, so it can be reopened from the network. */
    public final boolean reusable;

    ZipperKind(boolean portal, boolean reusable) {
        this.portal = portal;
        this.reusable = reusable;
    }

    public static ZipperKind byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SURFACE;
    }
}
