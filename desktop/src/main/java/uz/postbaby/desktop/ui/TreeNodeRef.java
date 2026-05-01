package uz.postbaby.desktop.ui;

import uz.postbaby.desktop.model.Collection;
import uz.postbaby.desktop.model.PostmanItem;

public class TreeNodeRef {
    public enum Kind {COLLECTION, FOLDER, REQUEST}

    public final Kind kind;
    public final Collection collection;
    public final PostmanItem item;
    public final PostmanItem parent;

    private TreeNodeRef(Kind kind, Collection collection, PostmanItem item, PostmanItem parent) {
        this.kind = kind;
        this.collection = collection;
        this.item = item;
        this.parent = parent;
    }

    public static TreeNodeRef collection(Collection c) {
        return new TreeNodeRef(Kind.COLLECTION, c, null, null);
    }

    public static TreeNodeRef folder(Collection c, PostmanItem folder, PostmanItem parent) {
        return new TreeNodeRef(Kind.FOLDER, c, folder, parent);
    }

    public static TreeNodeRef request(Collection c, PostmanItem req, PostmanItem parent) {
        return new TreeNodeRef(Kind.REQUEST, c, req, parent);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case COLLECTION -> collection.name == null ? "Collection" : collection.name;
            case FOLDER, REQUEST -> item.name == null ? "Untitled" : item.name;
        };
    }
}
