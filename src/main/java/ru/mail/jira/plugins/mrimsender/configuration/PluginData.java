package ru.mail.jira.plugins.mrimsender.configuration;

import java.util.List;

public interface PluginData {
    String getToken();
    void setToken(String token);

    boolean isEnabledByDefault();
    void setEnabledByDefault(boolean enabledByDefault);

    List<String> getNotifiedUserKeys();
    void setNotifiedUserKeys(List<String> notifiedUserKeys);
}
