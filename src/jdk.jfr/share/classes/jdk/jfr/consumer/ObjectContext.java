package jdk.jfr.consumer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;

final class ObjectContext {
    private final Map<ValueDescriptor, ObjectContext> contextLookup;

    final EventType eventType;
    final List<ValueDescriptor> fields;
    final TimeConverter timeConverter;

    public ObjectContext(EventType eventType, List<ValueDescriptor> fields, TimeConverter timeConverter) {
        this.contextLookup = new HashMap<>();
        this.eventType = eventType;
        this.fields = fields;
        this.timeConverter = timeConverter;
    }

    private ObjectContext(ObjectContext root, ValueDescriptor desc) {
        this.eventType = root.eventType;
        this.contextLookup = root.contextLookup;
        this.timeConverter = root.timeConverter;
        this.fields = desc.getFields();
    }

    public ObjectContext getInstance(ValueDescriptor desc) {
        ObjectContext h = contextLookup.get(desc);
        if (h == null) {
            h = new ObjectContext(this, desc);
            contextLookup.put(desc, h);
        }
        return h;
    }
}