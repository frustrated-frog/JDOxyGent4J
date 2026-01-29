package com.jd.oxygent.core.oxygent.schemas;

import com.jd.oxygent.core.oxygent.utils.CommonUtils;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Simple immutable container representing a Server-Sent Events (SSE) message.
 *
 * <p>This class represents the minimal structure required for SSE transport:
 * an identifier (`id`), an event name (`event`) and an event payload (`data`).
 * The payload is stored as an {@code Object} and serialized to JSON by
 * {@code CommonUtils.toJson(Object)} when converting to a map for SSE.
 */
@Data
public class SSEMessage<T> {
    /** Unique identifier for the message (generated automatically). */
    private String id = CommonUtils.generateShortUUID();
    /** Event type/name (defaults to "message"). */
    private String event = "message";
    /** Event payload. May be any object serializable by CommonUtils. */
    private T data = null;
    /** Retry interval ms (defaults to 3000). */
    private Integer retry = 3000;

    /**
     * Creates an SSEMessage with default event name "message" and empty payload.
     */
    public SSEMessage() {
    }

    /**
     * Creates an SSEMessage with the provided event and payload.
     *
     * @param event event name; if {@code null} the value defaults to "message"
     * @param data  event payload; if {@code null} the value defaults to an empty string
     */
    public SSEMessage(String event, T data) {
        this.event = event != null ? event : "message";
        this.data = data;
    }
}
