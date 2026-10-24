package com.lima.boxier;

import com.mojang.blaze3d.platform.InputConstants;

public final class BrowserKeyMapper {
    private BrowserKeyMapper() {
    }

    public static KeyData map(int glfwKey) {
        if (glfwKey >= InputConstants.KEY_A && glfwKey <= InputConstants.KEY_Z) {
            char lower = (char) ('a' + (glfwKey - InputConstants.KEY_A));
            char upper = (char) ('A' + (glfwKey - InputConstants.KEY_A));
            return new KeyData(String.valueOf(lower), "Key" + upper, glfwKey);
        }
        if (glfwKey >= InputConstants.KEY_0 && glfwKey <= InputConstants.KEY_9) {
            char digit = (char) ('0' + (glfwKey - InputConstants.KEY_0));
            return new KeyData(String.valueOf(digit), "Digit" + digit, glfwKey);
        }

        return switch (glfwKey) {
            case InputConstants.KEY_SPACE -> new KeyData(" ", "Space", 32);
            case InputConstants.KEY_RETURN -> new KeyData("Enter", "Enter", 13);
            case InputConstants.KEY_BACKSPACE -> new KeyData("Backspace", "Backspace", 8);
            case InputConstants.KEY_TAB -> new KeyData("Tab", "Tab", 9);
            case InputConstants.KEY_ESCAPE -> new KeyData("Escape", "Escape", 27);
            case InputConstants.KEY_DELETE -> new KeyData("Delete", "Delete", 46);
            case InputConstants.KEY_INSERT -> new KeyData("Insert", "Insert", 45);
            case InputConstants.KEY_HOME -> new KeyData("Home", "Home", 36);
            case InputConstants.KEY_END -> new KeyData("End", "End", 35);
            case InputConstants.KEY_PAGEUP -> new KeyData("PageUp", "PageUp", 33);
            case InputConstants.KEY_PAGEDOWN -> new KeyData("PageDown", "PageDown", 34);
            case InputConstants.KEY_LEFT -> new KeyData("ArrowLeft", "ArrowLeft", 37);
            case InputConstants.KEY_UP -> new KeyData("ArrowUp", "ArrowUp", 38);
            case InputConstants.KEY_RIGHT -> new KeyData("ArrowRight", "ArrowRight", 39);
            case InputConstants.KEY_DOWN -> new KeyData("ArrowDown", "ArrowDown", 40);
            case InputConstants.KEY_LSHIFT -> new KeyData("Shift", "ShiftLeft", 16);
            case InputConstants.KEY_RSHIFT -> new KeyData("Shift", "ShiftRight", 16);
            case InputConstants.KEY_LCONTROL -> new KeyData("Control", "ControlLeft", 17);
            case InputConstants.KEY_RCONTROL -> new KeyData("Control", "ControlRight", 17);
            case InputConstants.KEY_LALT -> new KeyData("Alt", "AltLeft", 18);
            case InputConstants.KEY_RALT -> new KeyData("Alt", "AltRight", 18);
            case InputConstants.KEY_MINUS -> new KeyData("-", "Minus", 189);
            case InputConstants.KEY_EQUALS -> new KeyData("=", "Equal", 187);
            case InputConstants.KEY_LBRACKET -> new KeyData("[", "BracketLeft", 219);
            case InputConstants.KEY_RBRACKET -> new KeyData("]", "BracketRight", 221);
            case InputConstants.KEY_BACKSLASH -> new KeyData("\\", "Backslash", 220);
            case InputConstants.KEY_SEMICOLON -> new KeyData(";", "Semicolon", 186);
            case InputConstants.KEY_APOSTROPHE -> new KeyData("'", "Quote", 222);
            case InputConstants.KEY_GRAVE -> new KeyData("`", "Backquote", 192);
            case InputConstants.KEY_COMMA -> new KeyData(",", "Comma", 188);
            case InputConstants.KEY_PERIOD -> new KeyData(".", "Period", 190);
            case InputConstants.KEY_SLASH -> new KeyData("/", "Slash", 191);
            case InputConstants.KEY_F1 -> new KeyData("F1", "F1", 112);
            case InputConstants.KEY_F2 -> new KeyData("F2", "F2", 113);
            case InputConstants.KEY_F3 -> new KeyData("F3", "F3", 114);
            case InputConstants.KEY_F4 -> new KeyData("F4", "F4", 115);
            case InputConstants.KEY_F5 -> new KeyData("F5", "F5", 116);
            case InputConstants.KEY_F6 -> new KeyData("F6", "F6", 117);
            case InputConstants.KEY_F7 -> new KeyData("F7", "F7", 118);
            case InputConstants.KEY_F8 -> new KeyData("F8", "F8", 119);
            case InputConstants.KEY_F9 -> new KeyData("F9", "F9", 120);
            case InputConstants.KEY_F10 -> new KeyData("F10", "F10", 121);
            case InputConstants.KEY_F11 -> new KeyData("F11", "F11", 122);
            case InputConstants.KEY_F12 -> new KeyData("F12", "F12", 123);
            default -> new KeyData("Unidentified", "", 0);
        };
    }

    public record KeyData(String key, String code, int keyCode) {
    }
}
