package de.malik.anticheat.startup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Map;

public final class StartupBanner {
    private static final NamedTextColor[] COLORS = {
            NamedTextColor.AQUA, NamedTextColor.BLUE, NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.YELLOW
    };
    private static final Map<Character, String[]> FONT = Map.ofEntries(
            Map.entry('A', new String[]{" ### ", "#   #", "#####", "#   #", "#   #"}),
            Map.entry('B', new String[]{"#### ", "#   #", "#### ", "#   #", "#### "}),
            Map.entry('C', new String[]{" ####", "#    ", "#    ", "#    ", " ####"}),
            Map.entry('E', new String[]{"#####", "#    ", "#### ", "#    ", "#####"}),
            Map.entry('H', new String[]{"#   #", "#   #", "#####", "#   #", "#   #"}),
            Map.entry('I', new String[]{"#####", "  #  ", "  #  ", "  #  ", "#####"}),
            Map.entry('K', new String[]{"#   #", "#  # ", "###  ", "#  # ", "#   #"}),
            Map.entry('L', new String[]{"#    ", "#    ", "#    ", "#    ", "#####"}),
            Map.entry('M', new String[]{"#   #", "## ##", "# # #", "#   #", "#   #"}),
            Map.entry('N', new String[]{"#   #", "##  #", "# # #", "#  ##", "#   #"}),
            Map.entry('T', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  "}),
            Map.entry('Y', new String[]{"#   #", " # # ", "  #  ", "  #  ", "  #  "}),
            Map.entry('7', new String[]{"#####", "    #", "   # ", "  #  ", "  #  "}),
            Map.entry('5', new String[]{"#####", "#    ", "#### ", "    #", "#### "}),
            Map.entry(' ', new String[]{"     ", "     ", "     ", "     ", "     "})
    );

    private StartupBanner() {
    }

    public static void send(ConsoleCommandSender console) {
        console.sendMessage(Component.empty());
        sendLargeText(console, "ANTI CHEAT");
        sendLargeText(console, "BY MALIK75");
        console.sendMessage(Component.text("                  Paper AntiCheat", NamedTextColor.GRAY));
        console.sendMessage(Component.empty());
    }

    private static void sendLargeText(ConsoleCommandSender console, String text) {
        for (int row = 0; row < 5; row++) {
            Component line = Component.empty();
            int column = 0;
            for (int letter = 0; letter < text.length(); letter++) {
                String pattern = FONT.get(text.charAt(letter))[row];
                for (int pixel = 0; pixel < pattern.length(); pixel++) {
                    boolean filled = pattern.charAt(pixel) == '#';
                    String cell = filled ? "██" : "  ";
                    if (filled) {
                        line = line.append(Component.text(cell, COLORS[column % COLORS.length]));
                    } else {
                        line = line.append(Component.text(cell));
                    }
                    column++;
                }
                line = line.append(Component.text(" "));
                column++;
            }
            console.sendMessage(line);
        }
        console.sendMessage(Component.empty());
    }
}
