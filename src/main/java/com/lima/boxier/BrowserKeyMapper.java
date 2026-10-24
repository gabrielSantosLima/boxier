package com.lima.boxier;

import org.lwjgl.sdl.SDLKeycode;

public final class BrowserKeyMapper {
    private BrowserKeyMapper() {
    }

    public static KeyData map(int key) {
        if (key >= 'a' && key <= 'z') {
            char lower = (char) key;
            char upper = Character.toUpperCase(lower);
            return new KeyData(String.valueOf(lower), "Key" + upper, lower - 32);
        }
        if (key >= 'A' && key <= 'Z') {
            char upper = (char) key;
            char lower = Character.toLowerCase(upper);
            return new KeyData(String.valueOf(lower), "Key" + upper, key);
        }
        if (key >= '0' && key <= '9') {
            char digit = (char) key;
            return new KeyData(String.valueOf(digit), "Digit" + digit, key);
        }

        return switch (key) {
            case SDLKeycode.SDLK_SPACE -> new KeyData(" ", "Space", 32);
            case SDLKeycode.SDLK_RETURN, SDLKeycode.SDLK_RETURN2 -> new KeyData("Enter", "Enter", 13);
            case SDLKeycode.SDLK_BACKSPACE -> new KeyData("Backspace", "Backspace", 8);
            case SDLKeycode.SDLK_TAB -> new KeyData("Tab", "Tab", 9);
            case SDLKeycode.SDLK_ESCAPE -> new KeyData("Escape", "Escape", 27);
            case SDLKeycode.SDLK_DELETE -> new KeyData("Delete", "Delete", 46);
            case SDLKeycode.SDLK_INSERT -> new KeyData("Insert", "Insert", 45);
            case SDLKeycode.SDLK_HOME -> new KeyData("Home", "Home", 36);
            case SDLKeycode.SDLK_END -> new KeyData("End", "End", 35);
            case SDLKeycode.SDLK_PAGEUP -> new KeyData("PageUp", "PageUp", 33);
            case SDLKeycode.SDLK_PAGEDOWN -> new KeyData("PageDown", "PageDown", 34);
            case SDLKeycode.SDLK_LEFT -> new KeyData("ArrowLeft", "ArrowLeft", 37);
            case SDLKeycode.SDLK_UP -> new KeyData("ArrowUp", "ArrowUp", 38);
            case SDLKeycode.SDLK_RIGHT -> new KeyData("ArrowRight", "ArrowRight", 39);
            case SDLKeycode.SDLK_DOWN -> new KeyData("ArrowDown", "ArrowDown", 40);
            case SDLKeycode.SDLK_LSHIFT, SDLKeycode.SDLK_RSHIFT -> new KeyData("Shift", "ShiftLeft", 16);
            case SDLKeycode.SDLK_LCTRL, SDLKeycode.SDLK_RCTRL -> new KeyData("Control", "ControlLeft", 17);
            case SDLKeycode.SDLK_LALT, SDLKeycode.SDLK_RALT -> new KeyData("Alt", "AltLeft", 18);
            case SDLKeycode.SDLK_LGUI, SDLKeycode.SDLK_RGUI -> new KeyData("Meta", "MetaLeft", 91);
            case SDLKeycode.SDLK_MINUS -> new KeyData("-", "Minus", 189);
            case SDLKeycode.SDLK_EQUALS -> new KeyData("=", "Equal", 187);
            case SDLKeycode.SDLK_LEFTBRACKET -> new KeyData("[", "BracketLeft", 219);
            case SDLKeycode.SDLK_RIGHTBRACKET -> new KeyData("]", "BracketRight", 221);
            case SDLKeycode.SDLK_BACKSLASH -> new KeyData("\\", "Backslash", 220);
            case SDLKeycode.SDLK_SEMICOLON -> new KeyData(";", "Semicolon", 186);
            case SDLKeycode.SDLK_APOSTROPHE -> new KeyData("'", "Quote", 222);
            case SDLKeycode.SDLK_GRAVE -> new KeyData("`", "Backquote", 192);
            case SDLKeycode.SDLK_COMMA -> new KeyData(",", "Comma", 188);
            case SDLKeycode.SDLK_PERIOD -> new KeyData(".", "Period", 190);
            case SDLKeycode.SDLK_SLASH -> new KeyData("/", "Slash", 191);
            case SDLKeycode.SDLK_F1 -> new KeyData("F1", "F1", 112);
            case SDLKeycode.SDLK_F2 -> new KeyData("F2", "F2", 113);
            case SDLKeycode.SDLK_F3 -> new KeyData("F3", "F3", 114);
            case SDLKeycode.SDLK_F4 -> new KeyData("F4", "F4", 115);
            case SDLKeycode.SDLK_F5 -> new KeyData("F5", "F5", 116);
            case SDLKeycode.SDLK_F6 -> new KeyData("F6", "F6", 117);
            case SDLKeycode.SDLK_F7 -> new KeyData("F7", "F7", 118);
            case SDLKeycode.SDLK_F8 -> new KeyData("F8", "F8", 119);
            case SDLKeycode.SDLK_F9 -> new KeyData("F9", "F9", 120);
            case SDLKeycode.SDLK_F10 -> new KeyData("F10", "F10", 121);
            case SDLKeycode.SDLK_F11 -> new KeyData("F11", "F11", 122);
            case SDLKeycode.SDLK_F12 -> new KeyData("F12", "F12", 123);
            default -> new KeyData("Unidentified", "", 0);
        };
    }

    public record KeyData(String key, String code, int keyCode) {
    }
}
