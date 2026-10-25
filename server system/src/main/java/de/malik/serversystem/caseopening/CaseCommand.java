package de.malik.serversystem.caseopening;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import de.malik.serversystem.ServerSystemBootstrap;

public class CaseCommand implements CommandExecutor {

    public static final String CASE_CONTAINER_MARKER = "__generic_case_container__";

    private final ServerSystemBootstrap plugin;
    private final CaseAdminMenu adminMenu;

    public CaseCommand(ServerSystemBootstrap plugin, CaseAdminMenu adminMenu) {
        this.plugin = plugin;
        this.adminMenu = adminMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("case")) {
            return handleCaseCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("case-set")) {
            return handleCaseSetCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("case-clear")) {
            return handleCaseClearCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("kristall") || command.getName().equalsIgnoreCase("crystals")) {
            return handleCrystalCommand(sender, args);
        }
        return false;
    }

    private boolean handleCaseSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("caseopening.admin")) {
            sender.sendMessage("§cDu hast keine Admin-Berechtigung.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDu musst ein Spieler sein.");
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage("§cVerwendung: /case-set");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        BlockState state = targetBlock == null ? null : targetBlock.getState();
        if (targetBlock == null || (!(state instanceof Barrel) && !(state instanceof Chest) && targetBlock.getType() != Material.ENDER_CHEST)) {
            sender.sendMessage("§cDu musst eine platzierte Kiste, ein Barrel oder eine Enderchest anvisieren.");
            return true;
        }

        if (targetBlock.getType() == Material.ENDER_CHEST) {
            plugin.getCaseOpeningManager().getSettings().setGenericEnderChest(targetBlock.getLocation(), true);
        } else if (state instanceof Barrel barrel) {
            barrel.getPersistentDataContainer().set(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING, CASE_CONTAINER_MARKER);
            barrel.update();
        } else if (state instanceof Chest chest) {
            chest.getPersistentDataContainer().set(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING, CASE_CONTAINER_MARKER);
            chest.update();
        }

        sender.sendMessage("§aDer Block wurde als generischer Case-Block markiert.");
        return true;
    }

    private boolean handleCaseClearCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("caseopening.admin")) {
            sender.sendMessage("§cDu hast keine Admin-Berechtigung.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDu musst ein Spieler sein.");
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage("§cVerwendung: /case-clear");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        BlockState state = targetBlock == null ? null : targetBlock.getState();
        if (targetBlock == null || (!(state instanceof Barrel) && !(state instanceof Chest) && targetBlock.getType() != Material.ENDER_CHEST)) {
            sender.sendMessage("§cDu musst eine platzierte Kiste, ein Barrel oder eine Enderchest anvisieren.");
            return true;
        }

        if (targetBlock.getType() == Material.ENDER_CHEST) {
            plugin.getCaseOpeningManager().getSettings().setGenericEnderChest(targetBlock.getLocation(), false);
        } else if (state instanceof Barrel barrel) {
            barrel.getPersistentDataContainer().remove(plugin.getCaseOpeningManager().getCaseKey());
            barrel.update();
        } else if (state instanceof Chest chest) {
            chest.getPersistentDataContainer().remove(plugin.getCaseOpeningManager().getCaseKey());
            chest.update();
        }

        sender.sendMessage("§aDie Kiste wurde wieder normalisiert.");
        return true;
    }

    private boolean handleCaseCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cDu musst ein Spieler sein, um das Menü zu öffnen.");
                return true;
            }
            plugin.getCaseOpeningManager().openCaseSelection(player);
            return true;
        }

        if (!sender.hasPermission("caseopening.admin")) {
            sender.sendMessage("§cDu hast keine Admin-Berechtigung.");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "admin" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cDieses Menü kann nur ingame geöffnet werden.");
                    return true;
                }
                adminMenu.openRoot(player);
                return true;
            }
            case "reload" -> {
                plugin.getCaseOpeningManager().getSettings().reload();
                plugin.getCaseOpeningManager().reloadCases();
                sender.sendMessage("§aCase-Konfiguration wurde neu geladen.");
                return true;
            }
            case "list" -> {
                sender.sendMessage(plugin.getCaseOpeningManager().getCaseStatusList());
                return true;
            }
            case "setprice" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cVerwendung: /case setprice <kiste> <preis>");
                    return true;
                }
                String caseId = args[1];
                int price;
                try {
                    price = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cDer Preis muss eine Zahl sein.");
                    return true;
                }
                if (price < 0) {
                    sender.sendMessage("§cDer Preis darf nicht negativ sein.");
                    return true;
                }
                plugin.getCaseOpeningManager().setCasePrice(caseId, price);
                sender.sendMessage("§aPreis für die Kiste §f" + caseId + " §aauf §f" + price + " §agesetzt.");
                return true;
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cVerwendung: /case give <kiste> [spieler]");
                    return true;
                }

                String caseId = args[1];
                Player target = null;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player player) {
                    target = player;
                }

                if (target == null) {
                    sender.sendMessage("§cSpieler nicht gefunden.");
                    return true;
                }

                if (plugin.getCaseOpeningManager().createCaseItem(caseId, 1) == null) {
                    sender.sendMessage("§cDiese Kiste existiert nicht.");
                    return true;
                }

                target.getInventory().addItem(plugin.getCaseOpeningManager().createCaseItem(caseId, 1));
                sender.sendMessage("§aKiste §f" + caseId + " §a wurde an §f" + target.getName() + " §agegeben.");
                return true;
            }
            default -> {
                sender.sendMessage("§aCase Befehle:\n§7/case\n/case admin\n/case give <kiste> [spieler]\n/case setprice <kiste> <preis>\n/case reload\n/case list");
                return true;
            }
        }
    }

    private boolean handleCrystalCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cDu musst ein Spieler sein.");
                return true;
            }
            if (!player.hasPermission("caseopening.kristalle.guthaben")) {
                sender.sendMessage("§cDu hast keine Berechtigung dafür.");
                return true;
            }
            int balance = plugin.getCrystalManager().getBalance(player.getUniqueId());
            player.sendMessage("§aDein Kristall-Guthaben: §f" + balance);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (!sender.hasPermission("caseopening.admin")) {
            sender.sendMessage("§cDu hast keine Admin-Berechtigung.");
            return true;
        }

        switch (action) {
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cVerwendung: /kristall give <spieler> <menge>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cSpieler nicht gefunden.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cDie Menge muss eine Zahl sein.");
                    return true;
                }
                if (amount <= 0) {
                    sender.sendMessage("§cDie Menge muss größer als 0 sein.");
                    return true;
                }
                plugin.getCrystalManager().addBalance(target.getUniqueId(), amount);
                sender.sendMessage("§aDu hast §f" + amount + " §aKristalle an §f" + target.getName() + " §agegeben.");
                return true;
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cVerwendung: /kristall set <spieler> <menge>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cSpieler nicht gefunden.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cDie Menge muss eine Zahl sein.");
                    return true;
                }
                if (amount < 0) {
                    sender.sendMessage("§cDas Guthaben darf nicht negativ sein.");
                    return true;
                }
                plugin.getCrystalManager().setBalance(target.getUniqueId(), amount);
                sender.sendMessage("§aKristall-Guthaben von §f" + target.getName() + " §aauf §f" + amount + " §agesetzt.");
                return true;
            }
            case "balance" -> {
                Player target = (args.length >= 2) ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    if (sender instanceof Player player) {
                        target = player;
                    } else {
                        sender.sendMessage("§cSpieler nicht gefunden.");
                        return true;
                    }
                }
                int balance = plugin.getCrystalManager().getBalance(target.getUniqueId());
                sender.sendMessage("§aGuthaben von §f" + target.getName() + "§a: §f" + balance + " Kristalle");
                return true;
            }
            default -> {
                sender.sendMessage("§aKristall Befehle:\n§7/kristall\n/kristall give <spieler> <menge>\n/kristall set <spieler> <menge>\n/kristall balance [spieler]");
                return true;
            }
        }
    }
}
