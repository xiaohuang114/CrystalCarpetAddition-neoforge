package crystal0404.crystalcarpetaddition.utils;

import org.jetbrains.annotations.NotNull;

class Key {
    private final boolean value;
    private final boolean defaultValue;

    public Key(@NotNull Boolean value, @NotNull Boolean defaultValue) {
        this.value = value;
        this.defaultValue = defaultValue;
    }

    public boolean getValue() {
        return this.value;
    }

    public boolean getDefaultValue() {
        return this.defaultValue;
    }
}
