package ru.privatenull.market;

public record FavoriteFilter(String id, Type type, String value) {
    public enum Type {
        MATERIAL,
        NAME
    }
}
