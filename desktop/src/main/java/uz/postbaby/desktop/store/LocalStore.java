package uz.postbaby.desktop.store;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.postbaby.desktop.config.AppConfig;
import uz.postbaby.desktop.model.AuthTokens;
import uz.postbaby.desktop.model.Collection;
import uz.postbaby.desktop.model.Environment;
import uz.postbaby.desktop.model.SavedTab;
import uz.postbaby.desktop.model.Team;
import uz.postbaby.desktop.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Filesystem-backed cache. Each top-level resource is its own JSON file so writes
 * are independent. Writes are atomic: serialize → temp file → atomic rename.
 */
public final class LocalStore {

    private static final Logger LOG = LoggerFactory.getLogger(LocalStore.class);

    private final Path root;

    public LocalStore() {
        this.root = AppConfig.dataDir();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data dir " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    /* -------- Tokens -------- */

    public AuthTokens loadTokens() {
        return readJson("tokens.json", AuthTokens.class);
    }

    public void saveTokens(AuthTokens tokens) {
        writeJson("tokens.json", tokens);
    }

    public void clearTokens() {
        delete("tokens.json");
    }

    /* -------- Active team -------- */

    public Long loadActiveTeamId() {
        Settings s = readJson("settings.json", Settings.class);
        return s == null ? null : s.activeTeamId;
    }

    public void saveActiveTeamId(Long teamId) {
        Settings s = readJson("settings.json", Settings.class);
        if (s == null) s = new Settings();
        s.activeTeamId = teamId;
        writeJson("settings.json", s);
    }

    public Long loadActiveEnvironmentId() {
        Settings s = readJson("settings.json", Settings.class);
        return s == null ? null : s.activeEnvironmentId;
    }

    public void saveActiveEnvironmentId(Long envId) {
        Settings s = readJson("settings.json", Settings.class);
        if (s == null) s = new Settings();
        s.activeEnvironmentId = envId;
        writeJson("settings.json", s);
    }

    public static class Settings {
        public Long activeTeamId;
        public Long activeEnvironmentId;
        public String activeTabId;
        /**
         * "dark" | "light". Defaults to dark.
         */
        public String theme;
    }

    public String loadTheme() {
        Settings s = readJson("settings.json", Settings.class);
        return s == null || s.theme == null ? "dark" : s.theme;
    }

    public void saveTheme(String theme) {
        Settings s = readJson("settings.json", Settings.class);
        if (s == null) s = new Settings();
        s.theme = theme;
        writeJson("settings.json", s);
    }

    public String loadActiveTabId() {
        Settings s = readJson("settings.json", Settings.class);
        return s == null ? null : s.activeTabId;
    }

    public void saveActiveTabId(String tabId) {
        Settings s = readJson("settings.json", Settings.class);
        if (s == null) s = new Settings();
        s.activeTabId = tabId;
        writeJson("settings.json", s);
    }

    /* -------- Saved tabs (per-user) -------- */

    public List<SavedTab> loadTabs(long userId) {
        List<SavedTab> list = readJson("user_" + userId + "_tabs.json",
                new TypeReference<List<SavedTab>>() {
                });
        return list == null ? new ArrayList<>() : list;
    }

    public void saveTabs(long userId, List<SavedTab> tabs) {
        writeJson("user_" + userId + "_tabs.json", tabs);
    }

    /* -------- Teams -------- */

    public List<Team> loadTeams() {
        List<Team> list = readJson("teams.json", new TypeReference<List<Team>>() {
        });
        return list == null ? new ArrayList<>() : list;
    }

    public void saveTeams(List<Team> teams) {
        writeJson("teams.json", teams);
    }

    /* -------- Collections (per team) -------- */

    public List<Collection> loadCollections(long teamId) {
        List<Collection> list = readJson("team_" + teamId + "_collections.json",
                new TypeReference<List<Collection>>() {
                });
        return list == null ? new ArrayList<>() : list;
    }

    public void saveCollections(long teamId, List<Collection> collections) {
        writeJson("team_" + teamId + "_collections.json", collections);
    }

    /* -------- Environments (per team) -------- */

    public List<Environment> loadEnvironments(long teamId) {
        List<Environment> list = readJson("team_" + teamId + "_environments.json",
                new TypeReference<List<Environment>>() {
                });
        return list == null ? new ArrayList<>() : list;
    }

    public void saveEnvironments(long teamId, List<Environment> envs) {
        writeJson("team_" + teamId + "_environments.json", envs);
    }

    /* -------- File primitives -------- */

    private <T> T readJson(String name, Class<T> type) {
        Path p = root.resolve(name);
        if (!Files.exists(p)) return null;
        try {
            return Json.parse(Files.readString(p), type);
        } catch (Exception e) {
            LOG.warn("Failed to read {}: {}", p, e.getMessage());
            return null;
        }
    }

    private <T> T readJson(String name, TypeReference<T> type) {
        Path p = root.resolve(name);
        if (!Files.exists(p)) return null;
        try {
            return Json.parse(Files.readString(p), type);
        } catch (Exception e) {
            LOG.warn("Failed to read {}: {}", p, e.getMessage());
            return null;
        }
    }

    private void writeJson(String name, Object value) {
        Path p = root.resolve(name);
        // Per-write unique temp file so concurrent writers don't clobber each other's tmp
        Path tmp = root.resolve(name + "." + Thread.currentThread().threadId() + "." + System.nanoTime() + ".tmp");
        try {
            Files.writeString(tmp, Json.stringify(value));
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Failed to write {}: {}", p, e.getMessage());
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to write " + p, e);
        }
    }

    private void delete(String name) {
        try {
            Files.deleteIfExists(root.resolve(name));
        } catch (IOException ignored) {
        }
    }
}
