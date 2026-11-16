package com.myriad;

/** Common settings for new Fabric installations. */
public final class Config {
    public static final IntValue CHANNEL_SECONDS=new IntValue(3,1,60);
    public static final IntValue SPIRIT_PER_EXTRACT=new IntValue(25,1,500);
    public static final IntValue SECT_MAX_LEVEL=new IntValue(15,1,99);
    private Config() { }
    public static void load() {
        var file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("myriad-common.json");
        var json=new com.google.gson.JsonObject();
        try {
            if(java.nio.file.Files.exists(file))try(var reader=java.nio.file.Files.newBufferedReader(file)){json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}
            CHANNEL_SECONDS.read(json,"channelSeconds");SPIRIT_PER_EXTRACT.read(json,"spiritPerExtract");SECT_MAX_LEVEL.read(json,"sectMaxLevel");
            java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
        }catch(java.io.IOException|RuntimeException e){throw new IllegalStateException("Cannot read MYRIAD common configuration "+file,e);}
    }
    public static final class IntValue {
        private int value;private final int min,max;
        IntValue(int value,int min,int max){this.value=value;this.min=min;this.max=max;}
        public int get(){return value;}
        private void read(com.google.gson.JsonObject json,String key){if(json.has(key))value=Math.clamp(json.get(key).getAsInt(),min,max);json.addProperty(key,value);}
    }
}
