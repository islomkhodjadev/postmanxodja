package uz.postbaby.desktop.ui;

import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

public class EditingCell extends TableCell<KeyValueRow, String> {

    private TextField field;

    @Override
    public void startEdit() {
        if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable()) return;
        super.startEdit();
        if (field == null) createField();
        field.setText(getItem() == null ? "" : getItem());
        setText(null);
        setGraphic(field);
        field.selectAll();
        field.requestFocus();
    }

    @Override
    public void commitEdit(String newValue) {
        int row = getIndex();
        if (row >= 0 && row < getTableView().getItems().size()) {
            KeyValueRow data = getTableView().getItems().get(row);
            if (data != null && data.isAuto()) data.setAuto(false);
        }
        super.commitEdit(newValue);
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem() == null ? "" : getItem());
        setGraphic(null);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
            return;
        }
        if (isEditing()) {
            if (field != null) field.setText(item == null ? "" : item);
            setText(null);
            setGraphic(field);
        } else {
            setText(item == null ? "" : item);
            setGraphic(null);
        }
    }

    private void createField() {
        field = new TextField();
        field.getStyleClass().add("kv-cell-field");
        field.setOnAction(e -> commitEdit(field.getText()));
        field.focusedProperty().addListener((obs, was, isFocused) -> {
            if (was && !isFocused && isEditing()) {
                commitEdit(field.getText());
            }
        });
        field.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                ev.consume();
            } else if (ev.getCode() == KeyCode.TAB) {
                commitEdit(field.getText());
            }
        });
    }
}
