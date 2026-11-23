package com.example.worldborderevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Сохранение состояния границы в мире (данные Верхнего мира). */
public class BorderSavedData extends SavedData {
    public static final String NAME = "worldborderevent_border";
    public double radius = 10000.0;
    public double startRadius = 10000.0;
    public boolean running = false;
    public boolean finished = false;
    public long tick = 0;
    public float brightness = 1.4f;

    public static BorderSavedData load(CompoundTag tag) {
        BorderSavedData d = new BorderSavedData();
        if (tag.contains("radius")) d.radius = tag.getDouble("radius");
        d.startRadius = tag.contains("startRadius") ? tag.getDouble("startRadius") : d.radius;
        d.running = tag.getBoolean("running");
        d.finished = tag.getBoolean("finished");
        d.tick = tag.getLong("tick");
        if (tag.contains("brightness")) d.brightness = tag.getFloat("brightness");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putDouble("radius", radius);
        tag.putDouble("startRadius", startRadius);
        tag.putBoolean("running", running);
        tag.putBoolean("finished", finished);
        tag.putLong("tick", tick);
        tag.putFloat("brightness", brightness);
        return tag;
    }
}
