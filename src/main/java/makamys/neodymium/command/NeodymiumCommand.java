package makamys.neodymium.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import makamys.neodymium.Compat;
import makamys.neodymium.Compat.Warning;
import makamys.neodymium.Neodymium;
import makamys.neodymium.util.ChatUtil;
import makamys.neodymium.util.ChatUtil.MessageVerbosity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ClientCommandHandler;

public class NeodymiumCommand extends CommandBase {
    
    public static final EnumChatFormatting HELP_COLOR = EnumChatFormatting.BLUE;
    public static final EnumChatFormatting HELP_USAGE_COLOR = EnumChatFormatting.YELLOW;
    public static final EnumChatFormatting HELP_WARNING_COLOR = EnumChatFormatting.YELLOW;
    public static final EnumChatFormatting HELP_EMPHASIS_COLOR = EnumChatFormatting.DARK_AQUA;
    public static final EnumChatFormatting ERROR_COLOR = EnumChatFormatting.RED;
    
    private static Map<String, ISubCommand> subCommands = new HashMap<>();
    
    public static void init() {
        ClientCommandHandler.instance.registerCommand(new NeodymiumCommand());
        registerSubCommand("status", new StatusCommand());
        registerSubCommand("disable_advanced_opengl", new DisableAdvancedOpenGLCommand());
    }
    
    public static void registerSubCommand(String key, ISubCommand command) {
        subCommands.put(key, command);   
    }
    
    @Override
    public String getCommandName() {
        return "neodymium";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "";
    }
    
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if(args.length > 0) {
            ISubCommand subCommand = subCommands.get(args[0]);
            if(subCommand != null) {
                subCommand.processCommand(sender, args);
                return;
            }
        }
        throw new WrongUsageException(getCommandName() + " <" + String.join("|", subCommands.keySet()) + ">", new Object[0]);
    }
    
    public static void addChatMessage(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
    
    public static void addColoredChatMessage(ICommandSender sender, String text, EnumChatFormatting color) {
        addColoredChatMessage(sender, text, color, null);
    }
    
    public static void addColoredChatMessage(ICommandSender sender, String text, EnumChatFormatting color, Consumer<ChatComponentText> fixup) {
        ChatComponentText msg = new ChatComponentText(text);
        msg.getChatStyle().setColor(color);
        if(fixup != null) {
            fixup.accept(msg);
        }
        sender.addChatMessage(msg);
    }
    
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, subCommands.keySet().toArray(new String[0])) : null;
    }
    
    public static class StatusCommand implements ISubCommand {

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(Neodymium.renderer != null) {
                List<String> text = Neodymium.renderer.getDebugText(true);
                addChatMessages(sender, text);
            }
            Pair<List<Warning>, List<Warning>> allWarns = Neodymium.showCompatStatus(true);
            List<Warning> warns = allWarns.getLeft();
            List<Warning> criticalWarns = allWarns.getRight();
            for(Warning line : warns) {
                addColoredChatMessageWithAction(sender, "* " + line.text, HELP_WARNING_COLOR, line.action);
            }
            for(Warning line : criticalWarns) {
                addColoredChatMessageWithAction(sender, "* " + line.text, ERROR_COLOR, line.action);
            }
        }
        
        private void addColoredChatMessageWithAction(ICommandSender sender, String text, EnumChatFormatting color, Runnable action) {
            ChatComponentText msg = new ChatComponentText(text);
            msg.getChatStyle().setColor(color);
            msg.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "neodymium disable_advanced_opengl"));
            sender.addChatMessage(msg);
        }

        private static void addChatMessages(ICommandSender sender, Collection<String> messages) {
            for(String line : messages) {
                sender.addChatMessage(new ChatComponentText(line));
            }
        }
        
    }
    
    public static class DisableAdvancedOpenGLCommand implements ISubCommand {

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(Compat.disableAdvancedOpenGL()) {
                Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                ChatUtil.showNeoChatMessage(EnumChatFormatting.AQUA + "Disabled Advanced OpenGL.", MessageVerbosity.INFO);
                Minecraft.getMinecraft().renderGlobal.loadRenderers();
            }
        }
        
    }
    
}
