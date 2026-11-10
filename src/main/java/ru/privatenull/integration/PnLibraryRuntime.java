package ru.privatenull.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.tasks.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskSpec;
import org.pnfolder.pnlibrary.api.updates.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot;

import java.util.Map;
import java.util.logging.Level;

/** Managed pnLibrary integration loaded only after the dependency is enabled. */
public final class PnLibraryRuntime implements PnRuntime {

    public static final String MINIMUM_VERSION = "0.0.1-dev.6";
    private static final String MODULE_ID = "pnmarket";
    private static final int BSTATS_PROJECT_ID = 32716;
    private static final String FASTSTATS_TOKEN = "1f89459f95c33fecc6f6829ffe65ff61";
    private static final String POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnMarketPolicy.java";
    private static final String UPDATE_OWNER = "pnFolder";
    private static final String UPDATE_REPOSITORY = "pnMarket";

    private final JavaPlugin owner;
    private final PnLibrary runtime;
    private PluginRegistration registration;
    private ModuleContext module;

    private PnLibraryRuntime(JavaPlugin owner, PnLibrary runtime) {
        this.owner = owner;
        this.runtime = runtime;
    }

    public static PnRuntime connect(JavaPlugin owner) {
        PnLibrary runtime = PnLibraryProvider.get();
        if (!runtime.isAtLeastVersion(MINIMUM_VERSION)) {
            throw new IllegalStateException("pnMarket требует pnLibrary " + MINIMUM_VERSION
                    + " или новее, установлена " + runtime.getVersion());
        }
        return new PnLibraryRuntime(owner, runtime);
    }

    @Override
    public void register(String storageType) {
        if (registration != null) throw new IllegalStateException("pnMarket уже зарегистрирован в pnLibrary");
        registration = runtime.getPlugins().register(owner);
        Log.plugin(owner);
        try {
            module = registration.registerModule(MODULE_ID, builder -> builder
                    // A policy that cannot be checked (offline server, JRE without a compiler) never stops pnMarket;
                    // only an explicit denial from the policy does.
                    .remotePolicy(RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build())
                    .metrics(metrics -> metrics.bStats(BSTATS_PROJECT_ID).fastStats(FASTSTATS_TOKEN))
                    .diagnostics(owner.getDataFolder().toPath(), DiagnosticContainer.builder("pnmarket")
                            .snapshot(() -> Map.<String, Object>of(
                                    "version", owner.getDescription().getVersion(),
                                    "storage", storageType))
                            .configuration(DiagnosticConfiguration.builder("config.yml")
                                    .secretKeyRegex("(?i).*(password|passwd|secret|token|uri|url|user(name)?)$")
                                    .build())
                            .build())
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY, updater -> updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .configurationBackup(owner.getDataFolder().toPath(),
                                    "config.yml", "messages.yml", "gui.yml", "sounds.yml")
                            .artifact("(?i)^pnMarket-[0-9].*\\.jar$", 17)));
        } catch (RuntimeException failure) {
            closeRegistration();
            throw failure;
        }
        Log.attach(owner, module);
        ru.privatenull.support.text.ColorUtil.use(module.getComponents());
    }

    @Override
    public void enabled(String storageType) {
        if (module == null) return;
        module.getLifecycle().enabled()
                .ok("Хранилище", storageType)
                .show();
    }

    @Override
    public String updateStatus(String currentVersion) {
        UpdateRegistration updates = module == null ? null : module.getUpdates();
        if (updates == null) return "&7проверяется";
        UpdateSnapshot snapshot = updates.getSnapshot();
        return switch (snapshot.getState()) {
            case CHECKING, DOWNLOADING -> "&7проверяется";
            case UPDATE_AVAILABLE, UPDATE_STAGED -> {
                String latest = snapshot.getLatestVersion();
                yield "&eдоступно &f" + currentVersion + " &8→ &#D8DF9D"
                        + (latest == null || latest.isBlank() ? "неизвестно" : latest);
            }
            case FAILED -> "&cпроверка недоступна";
            case INCOMPATIBLE, BLOCKED -> "&eновая версия несовместима с сервером";
            case FROZEN -> "&7обновления приостановлены";
            case ROLLED_BACK -> "&7выполнен откат версии";
            case UP_TO_DATE -> "&aактуальная версия";
        };
    }

    @Override
    public String supportUrl() {
        return PnLibraryIdentity.SUPPORT_URL;
    }

    @Override
    public void registerCommands(ru.privatenull.command.PnMarketCommand actions) {
        if (module == null) throw new IllegalStateException("Модуль pnMarket ещё не зарегистрирован в pnLibrary");
        LibraryCommands.register(module, actions);
    }

    @Override
    public PnTasks tasks() {
        return tasks;
    }

    private final PnTasks tasks = new PnTasks() {
        @Override
        public void sync(Runnable action) {
            schedule("pnmarket-sync", TaskExecution.global(), 0L, 0L, action);
        }

        @Override
        public void async(Runnable action) {
            schedule("pnmarket-async", TaskExecution.async(), 0L, 0L, action);
        }

        @Override
        public Handle syncLater(Runnable action, long delayTicks) {
            return schedule("pnmarket-later", TaskExecution.global(), delayTicks, 0L, action);
        }

        @Override
        public Handle syncTimer(Runnable action, long delayTicks, long periodTicks) {
            return schedule("pnmarket-timer", TaskExecution.global(), delayTicks, periodTicks, action);
        }

        @Override
        public Handle asyncTimer(Runnable action, long delayTicks, long periodTicks) {
            return schedule("pnmarket-async-timer", TaskExecution.async(), delayTicks, periodTicks, action);
        }
    };

    private PnTasks.Handle schedule(String name, TaskExecution execution, long delayTicks, long periodTicks,
                                    Runnable action) {
        ModuleContext current = module;
        if (current == null || current.isClosed()) return PnTasks.NONE.syncLater(action, 0L);
        TaskSpec.Builder spec = TaskSpec.builder()
                .name(name)
                .execution(execution)
                .delayTicks(delayTicks)
                .action(context -> action.run());
        if (periodTicks > 0L) spec.intervalTicks(periodTicks);
        TaskHandle handle = current.getTasks().schedule(spec.build());
        return handle::cancel;
    }

    @Override
    public void close() {
        Log.detach();
        ModuleContext closing = module;
        module = null;
        if (closing != null && !closing.isClosed()) {
            try {
                closing.getLifecycle().disabled().show();
            } catch (RuntimeException failure) {
                owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение об остановке pnMarket", failure);
            }
        }
        closeRegistration();
    }

    private void closeRegistration() {
        PluginRegistration closing = registration;
        registration = null;
        if (closing != null && !closing.isClosed()) closing.close();
    }
}
