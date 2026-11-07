package com.cnethuka.kegare.data;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

/** {@code assets/kegare/sounds.json}, one entry per {@link KegareSounds#ALL}; runData fails if a file is missing. */
public final class KegareSoundProvider extends SoundDefinitionsProvider {
    public KegareSoundProvider(PackOutput output, ExistingFileHelper files) {
        super(output, Kegare.MODID, files);
    }

    @Override
    public void registerSounds() {
        for (KegareSounds.Sound s : KegareSounds.ALL) {
            add(s.event().get(), definition().subtitle(s.subtitle()).with(sound(Kegare.id(s.file()))));
        }
    }
}
