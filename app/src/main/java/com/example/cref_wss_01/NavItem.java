package com.example.cref_wss_01;

public abstract class NavItem {
    public static final int TYPE_CATEGORY = 0;
    public static final int TYPE_SUB_CATEGORY = 1;
    public static final int TYPE_QUESTION = 2;

    abstract public int getType();
}
