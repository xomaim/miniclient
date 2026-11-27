#!/bin/bash
# Prints the 26.2 API of the Minecraft classes Mini Client uses, so the port can be checked against the real names.
out=/tmp/lg/probe.txt; : > "$out"
MCV=$(grep "^minecraft_version=" ports/*/gradle.properties 2>/dev/null | head -0; grep "^minecraft_version=" "$PORT_DIR/gradle.properties" | cut -d= -f2)
JARS=$(find ~/.gradle /home/runner/work -name "*.jar" 2>/dev/null | grep -iE "minecraft-(client|merged|common)|minecraft-clientOnly|/minecraft/.*client" | grep -v sources | grep -F -- "$MCV")
echo "JARS:" >> "$out"; echo "$JARS" >> "$out"
CP=$(echo "$JARS" | tr '\n' ':')
J=$(echo "$JARS" | head -1)
for jar in $JARS; do unzip -Z1 "$jar" 2>/dev/null; done | grep -E '^net/minecraft/(client/gui/[^$]*|resources/[^/$]*|client/Minecraft|client/Options|client/User|client/server/IntegratedServer|util/HttpUtil|Util|client/renderer/texture/(DynamicTexture|TextureManager|AbstractTexture)|client/renderer/RenderPipelines|client/resources/(SkinManager|PlayerSkin)[^$]*|client/gui/Gui|client/multiplayer/[^$]*|client/KeyMapping|client/input/[^$]*|world/entity/player/PlayerSkin)\.class$' | sort -u > /tmp/cls.txt
echo "=== CLASSES" >> "$out"; cat /tmp/cls.txt >> "$out"
for c in net.minecraft.client.gui.GuiGraphicsExtractor net.minecraft.client.gui.GuiGraphics net.minecraft.client.gui.components.PlayerFaceRenderer net.minecraft.client.renderer.RenderType com.mojang.blaze3d.vertex.PoseStack net.minecraft.resources.ResourceLocation net.minecraft.client.resources.PlayerSkin net.minecraft.client.renderer.texture.SpriteContents net.minecraft.client.gui.screens.Screen net.minecraft.client.gui.components.AbstractWidget \
  net.minecraft.client.gui.components.AbstractButton net.minecraft.client.gui.components.AbstractSliderButton net.minecraft.client.gui.components.EditBox \
  net.minecraft.client.gui.components.PlayerSkinWidget net.minecraft.client.gui.screens.LoadingOverlay net.minecraft.client.Minecraft net.minecraft.client.gui.Gui \
  net.minecraft.client.gui.components.events.ContainerEventHandler net.minecraft.client.gui.components.events.GuiEventListener \
  net.minecraft.client.renderer.texture.DynamicTexture net.minecraft.client.renderer.texture.AbstractTexture net.minecraft.client.renderer.texture.TextureManager \
  net.minecraft.client.resources.SkinManager net.minecraft.client.server.IntegratedServer net.minecraft.util.HttpUtil net.minecraft.client.User \
  net.minecraft.client.gui.Font net.minecraft.client.gui.screens.TitleScreen net.minecraft.client.gui.screens.PauseScreen net.minecraft.client.Options \
  net.minecraft.client.gui.components.Button net.minecraft.client.gui.components.PlayerFaceRenderer net.minecraft.client.gui.components.ChatComponent \
  net.minecraft.resources.Identifier net.minecraft.client.renderer.RenderPipelines com.mojang.blaze3d.platform.Window com.mojang.blaze3d.platform.NativeImage \
  net.minecraft.client.KeyMapping net.minecraft.client.input.MouseButtonEvent net.minecraft.client.input.KeyEvent net.minecraft.client.input.CharacterEvent \
  net.minecraft.client.gui.screens.worldselection.SelectWorldScreen net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen net.minecraft.client.gui.screens.options.OptionsScreen \
  net.minecraft.client.gui.screens.ConnectScreen net.minecraft.client.multiplayer.ServerData net.minecraft.client.multiplayer.resolver.ServerAddress \
  net.minecraft.client.gui.components.Renderable net.minecraft.client.gui.layouts.LayoutElement; do
  echo "=== $c" >> "$out"
  javap -cp "$CP" -public "$c" >> "$out" 2>&1 || true
done
# Fabric API HUD + screen events
FAPI=$(find ~/.gradle -name "fabric-rendering-v1*.jar" -o -name "fabric-screen-api-v1*.jar" -o -name "fabric-lifecycle-events-v1*.jar" -o -name "fabric-key-binding-api-v1*.jar" 2>/dev/null | grep -v sources | grep -v -- "-dev" ; find ~/.gradle /home/runner/work -path "*remapped*" -name "fabric-rendering-v1*.jar" 2>/dev/null | head -2)
echo "=== FABRIC JARS" >> "$out"; echo "$FAPI" >> "$out"
for jar in $FAPI; do unzip -Z1 "$jar" | grep -E 'api/.*\.class$' | grep -v '\$' >> "$out"; done
for c in net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement \
  net.fabricmc.fabric.api.client.screen.v1.Screens net.fabricmc.fabric.api.client.screen.v1.ScreenEvents net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper \
  net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback; do
  echo "=== $c" >> "$out"; javap -cp "$CP:$(echo "$FAPI" | tr '\n' ':')" -public "$c" >> "$out" 2>&1 || true
done
# round 2: protected/private members of classes we extend or mix into, plus moved classes
for c in net.minecraft.client.gui.components.AbstractWidget net.minecraft.client.gui.components.AbstractButton net.minecraft.client.gui.components.AbstractSliderButton \
  'net.minecraft.client.gui.components.AbstractWidget$WithInactiveMessage' net.minecraft.client.gui.screens.Screen net.minecraft.client.gui.Hud net.minecraft.client.gui.components.PlayerFaceExtractor \
  net.minecraft.util.Util 'net.minecraft.server.MinecraftServer$MultiplayerScope' net.minecraft.client.DeltaTracker net.minecraft.client.input.InputWithModifiers \
  com.mojang.realmsclient.RealmsMainScreen net.minecraft.client.gui.screens.ConnectScreen net.minecraft.client.multiplayer.ServerData net.minecraft.client.gui.screens.options.OptionsScreen; do
  echo "=== P $c" >> "$out"; javap -cp "$CP" -protected "$c" 2>&1 | grep -v lambda >> "$out" || true
done
echo "=== P LoadingOverlay private" >> "$out"; javap -cp "$CP" -private net.minecraft.client.gui.screens.LoadingOverlay >> "$out" 2>&1
echo "=== Options fields" >> "$out"; javap -cp "$CP" -public net.minecraft.client.Options | grep -iE "hideGui|darkMojang|Mojang|gui|OptionInstance<java.lang.Boolean>" >> "$out" 2>&1
echo "=== Util candidates" >> "$out"; grep -E '/Util\.class$' /tmp/cls.txt >> "$out"; for jar in $JARS; do unzip -Z1 "$jar"; done | grep -E '(^|/)Util\.class$|KeyMapping|Realms.*MainScreen' | sort -u >> "$out"
for jar in $(find ~/.gradle -name "fabric-*.jar" | grep -v sources); do unzip -Z1 "$jar" | grep -iE 'key(binding|mapping).*Helper' ; done >> "$out" 2>/dev/null
echo "=== LWJGL packages" >> "$out"; for jar in $(find ~/.gradle -name "lwjgl*.jar" | grep -v -e sources -e natives); do echo "-- $(basename $jar)"; unzip -Z1 "$jar" | grep -E '\.class$' | sed 's#/[^/]*$##' | sort -u | head -8; done >> "$out" 2>&1
echo "=== Window full" >> "$out"; javap -cp "$CP" -public com.mojang.blaze3d.platform.Window >> "$out" 2>&1
for jar in $JARS; do unzip -Z1 "$jar"; done | grep -iE 'platform/(Window|Input|Mouse|SDL|Glfw)|/MouseHandler|/KeyboardHandler' | sort -u >> "$out"
echo "=== IntegratedServer publish" >> "$out"; javap -cp "$CP" -public net.minecraft.client.server.IntegratedServer | grep -i publish >> "$out" 2>&1
echo "probe done: $(wc -l < $out) lines"
