package edu.algorithm.entity;


import lombok.Data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Data
public class Invocation {

    private static final long serialVersionUID = -4355285085441097045L;
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] arguments;
    private Map<String, Object> attachments;

    private transient Map<Object, Object> attributes = Collections.synchronizedMap(new HashMap<>());

    public void setAttachment(String key, Object value) {
        setObjectAttachment(key, value);
    }

    public Map<Object, Object> getAttributes() {
        return attributes;
    }
    public void setObjectAttachment(String key, Object value) {
        try {

            if (attachments == null) {
                attachments = new HashMap<>();
            }
            attachments.put(key, value);
        } finally {

        }
    }
}
