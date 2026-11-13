
# 万象 · MYRIAD

我的世界万象mod开发。

多题材角色团队对抗玩法模组，原名《仙凡劫》。未来角色不限于修仙，奇幻、科幻、现代、历史、神话、机械等题材均可按角色定位与机制设计。

中文名：**万象**；英文名：**MYRIAD**。现有地图主题保留。正式可选阵容为夜影、天刑、焚诀、墨尘、素岚、凌渊、寒泽、青萝、赤霄、云笙、朔、刻衡和伊珂，共十三人、104 个独立候选技能。十名旧版角色已退役。支持行动的起手、攻击恢复、自主位移与独立弹体按局部缓时推进；兵线、普通野怪及原版普通／药／光灵箭也已接入。视角、握持、模型、VFX 和听感按用户要求后置统一调整；功能证据与新测试世界交付见 [十三人整合验证](docs/verification/roster13-final28.md)。

当前目标 Minecraft 26.2 / NeoForge 26.2.0.59 / Java 25。内部模组 ID 与资源命名空间为 `myriad`，Java 包为 `com.myriad`，命令为 `/myriad`，构建包为 `myriad-0.1.0.jar`。

2026-11-12 的内部命名迁移不兼容旧身份的存档数据与旧客户端；请先备份，在新世界测试，客户端和服务端同时更新。不要同时加载新旧模组，也不要直接用新包打开唯一一份旧世界。

开发接手请先阅读：[协作开发与内部命名](docs/协作开发与内部命名.md)。

仓库内保留可直接构建的源码与游戏资源。地图存档、个人配置、Gradle 缓存和构建产物不在源码上传范围内；不要把此仓库当作完整的 Minecraft 游戏实例。

以下保留原 MDK 环境说明。

Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
