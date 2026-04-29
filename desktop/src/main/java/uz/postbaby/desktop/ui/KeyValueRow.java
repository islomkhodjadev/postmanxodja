package uz.postbaby.desktop.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class KeyValueRow {
    private final SimpleStringProperty key = new SimpleStringProperty();
    private final SimpleStringProperty value = new SimpleStringProperty();
    private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);
    private boolean auto = false;

    public KeyValueRow() {
    }

    public KeyValueRow(String k, String v) {
        this.key.set(k == null ? "" : k);
        this.value.set(v == null ? "" : v);
    }

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean a) {
        this.auto = a;
    }

    public String getKey() {
        return key.get();
    }

    public void setKey(String k) {
        key.set(k);
    }

    public SimpleStringProperty keyProperty() {
        return key;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String v) {
        value.set(v);
    }

    public SimpleStringProperty valueProperty() {
        return value;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean e) {
        enabled.set(e);
    }

    public SimpleBooleanProperty enabledProperty() {
        return enabled;
    }

    public boolean isBlank() {
        return (key.get() == null || key.get().isBlank()) && (value.get() == null || value.get().isBlank());
    }
}
