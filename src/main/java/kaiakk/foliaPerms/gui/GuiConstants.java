package kaiakk.foliaPerms.gui;

/**
 * Central constants for GUI slot management and sizing.
 * Version: 1.13.0
 */
public final class GuiConstants {
    private GuiConstants() {} // Prevent instantiation

    // Inventory sizes
    public static final int MAIN_MENU_SIZE = 27;
    public static final int PERMISSIONS_PAGE_SIZE = 54;
    public static final int PERMS_PER_PAGE = 45;

    // Navigation buttons
    public static final int BUTTON_BACK = 45;
    public static final int BUTTON_CENTER = 48;
    public static final int BUTTON_EXIT = 49;
    public static final int BUTTON_NEXT = 53;

    // Inventory borders
    public static final int FOOTER_START = 45;

    // Main menu slots
    public static final int SLOT_GROUPS = 11;
    public static final int SLOT_PLAYERS = 15;

    // List navigation
    public static final int LIST_BACK_OFFSET = 5; // size - 5

    // Common checks
    public static boolean isNavigationButton(int slot) {
        return slot == BUTTON_BACK || slot == BUTTON_CENTER || slot == BUTTON_EXIT || slot == BUTTON_NEXT;
    }

    public static boolean isFooter(int slot) {
        return slot >= FOOTER_START;
    }
}
