package com.cnethuka.kegare.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class KegareLanguageProviderTest {
    @Test
    void validatesOnlyTheNamespaceWhoseLangFileItOwns() {
        assertTrue(KegareLanguageProvider.requiresTranslations(ResourceLocation.fromNamespaceAndPath("kegare", "frost_shard")));
        assertTrue(KegareLanguageProvider.requiresTranslations(ResourceLocation.fromNamespaceAndPath("kegare", "splinter")));
        assertFalse(KegareLanguageProvider.requiresTranslations(ResourceLocation.fromNamespaceAndPath("addon", "frost_shard")));
        assertFalse(KegareLanguageProvider.requiresTranslations(ResourceLocation.fromNamespaceAndPath("addon", "custom_technique")));
    }
}
