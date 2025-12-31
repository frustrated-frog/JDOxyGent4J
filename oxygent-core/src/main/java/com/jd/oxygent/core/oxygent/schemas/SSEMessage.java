package com.jd.oxygent.core.oxygent.schemas;

import com.jd.oxygent.core.oxygent.utils.CommonUtils;
import lombok.Data;


/**
 * Simple immutable container representing a Server-Sent Events (SSE) message.
 *
 * <p>This class represents the minimal structure required for SSE transport:
 * an identifier (`id`), an event name (`event`) and an event payload (`data`).
 * The payload is stored as an {@code Object} and serialized to JSON by
 * {@code CommonUtils.toJson(Object)} when converting to a map for SSE.
 */
@Data
public class SSEMessage {
    /** Unique identifier for the message (generated automatically). */
    private final String id;
    /** Event type/name (defaults to "message"). */
    private final String event;
    /** Event payload. May be any object serializable by CommonUtils. */
    private final Object data;

    /**
     * Creates an SSEMessage with default event name "message" and empty payload.
     */
    public SSEMessage() {
        this.id = CommonUtils.generateShortUUID();
        this.event = "message";
        this.data = "";
    }

    /**
     * Creates an SSEMessage with the provided event and payload.
     *
     * @param event event name; if {@code null} the value defaults to "message"
     * @param data  event payload; if {@code null} the value defaults to an empty string
     */
    public SSEMessage(String event, Object data) {
        this.id = CommonUtils.generateShortUUID();
        this.event = event != null ? event : "message";
        this.data = data != null ? data : "";
    }

    /**
     * Converts this message into an ordered map suitable for SSE serialization.
     * Keys are: "id", "event", and "data" (JSON string).
     *
     * @return ordered map with string values for SSE transport
     */
    public java.util.Map<String, String> toSse() {
        java.util.Map<String, String> sseMap = new java.util.LinkedHashMap<>();
        sseMap.put("id", this.id);
        sseMap.put("event", this.event);
        sseMap.put("data", CommonUtils.toJson(this.data));
        return sseMap;
    }

    /** Returns the message id. */
    public String getId() { return id; }

    /** Returns the event name. */
    public String getEvent() { return event; }

    /** Returns the raw data object (not serialized). */
    public Object getData() { return data; }

    @Override
    public String toString() {
        return "SSEMessage{id='" + id + "', event='" + event + "', data=" + CommonUtils.toJson(data) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SSEMessage that = (SSEMessage) o;
        return java.util.Objects.equals(id, that.id)
                && java.util.Objects.equals(event, that.event)
                && java.util.Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, event, data);
    }
}
