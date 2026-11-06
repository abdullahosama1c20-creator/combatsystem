package dev.combatcore;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.net.URI;
import java.util.Objects;

/** Registers the bundled kill-achievements datapack (resources/datapack) with the server. */
public class CombatBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            DatapackRegistrar registrar = event.registrar();
            try {
                URI uri = Objects.requireNonNull(CombatBootstrap.class.getResource("/datapack")).toURI();
                registrar.discoverPack(uri, "kill_achievements");
            } catch (Exception ex) {
                context.getLogger().error("Could not register the bundled kill-achievements datapack", ex);
            }
        });
    }
}
