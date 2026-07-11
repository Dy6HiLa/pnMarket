package ru.privatenull.config;

import ru.privatenull.market.MarketFilter.SortType;

import java.util.Locale;

public final class GuiLabels {
    private final MessagesConfig messages;

    public GuiLabels(MessagesConfig messages) {
        this.messages = messages;
    }

    public String sort(SortType sort) {
        return messages.message("gui.sort." + sort.name().toLowerCase(Locale.ROOT));
    }
}
