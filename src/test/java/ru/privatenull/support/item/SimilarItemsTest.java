package ru.privatenull.support.item;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimilarItemsTest {
    @Test
    void brokenSkullComparisonDoesNotThrow() {
        ItemStack first = mock(ItemStack.class);
        ItemStack second = mock(ItemStack.class);
        when(first.isSimilar(second)).thenThrow(new NullPointerException("serializedProfile is null"));
        assertDoesNotThrow(() -> SimilarItems.isEqual(first, second));
        assertFalse(SimilarItems.isEqual(first, second));
    }

    @Test
    void nullsAndSameInstance() {
        ItemStack item = mock(ItemStack.class);
        assertTrue(SimilarItems.isSimilar(item, item));
        assertFalse(SimilarItems.isSimilar(item, null));
        assertFalse(SimilarItems.isSimilar(null, item));
    }
}
